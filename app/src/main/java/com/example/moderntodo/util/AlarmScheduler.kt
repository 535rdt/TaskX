package com.example.moderntodo.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun schedule(context: Context, task: TaskEntity) {
        if (!SettingsRepository.notificationsEnabledFlow.value) {
            cancel(context, task.id ?: 0)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val taskId = task.id ?: return

        // Cancel previous alarms for this task first
        cancel(context, taskId)

        if (task.isCompleted) {
            return
        }

        val triggerTime = computeTriggerTime(task)
        if (triggerTime == null) {
            Log.d(TAG, "Task '${task.title}' has no scheduled time or date. Skipping.")
            return
        }

        val now = System.currentTimeMillis()
        if (triggerTime <= now) {
            Log.d(TAG, "Task '${task.title}' trigger time is in the past ($triggerTime vs now $now). Skipping.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_TITLE", task.title)
            putExtra("EXTRA_DESC", if (task.description.isNotBlank()) task.description else "Task is due now!")
            putExtra("EXTRA_TASK_ID", taskId)
            putExtra("EXTRA_RINGTONE_URI", task.ringtoneUri)
            putExtra("EXTRA_HAS_ALARM", task.hasAlarm)
        }

        // Schedule exact alarm for the deadline
        scheduleExact(context, alarmManager, triggerTime, taskId, 0, intent)
    }

    private fun scheduleExact(
        context: Context,
        alarmManager: AlarmManager,
        alarmTime: Long,
        taskId: Int,
        index: Int,
        intent: Intent
    ) {
        val requestCode = taskId * 100 + index
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            }
            Log.d(TAG, "Successfully scheduled alarm for task $taskId at $alarmTime (in ${(alarmTime - System.currentTimeMillis()) / 1000}s)")
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to setAndAllowWhileIdle: ${se.message}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback alarm failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm", e)
        }
    }

    fun computeTriggerTime(task: TaskEntity): Long? {
        val now = System.currentTimeMillis()

        // 1. If explicit time is set
        if (task.dueTime != null) {
            return task.dueTime
        }

        // 2. If only dueDate is set
        if (task.dueDate != null) {
            val defaultReminderOffset = SettingsRepository.defaultReminderTimeFlow.value
            val cal = Calendar.getInstance().apply { timeInMillis = task.dueDate }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val trigger = cal.timeInMillis + defaultReminderOffset
            if (trigger > now) {
                return trigger
            }

            // If the date is today and default time has passed, trigger 1 minute from now
            val todayCal = Calendar.getInstance()
            val isToday = todayCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    todayCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

            if (isToday) {
                return now + 60 * 1000L
            }

            return trigger
        }

        // 3. If neither is set, but hasAlarm is true, schedule reminder 1 minute ahead
        if (task.hasAlarm) {
            return now + 60 * 1000L
        }

        return null
    }

    fun cancel(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        for (index in 0..10) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {}
        }
    }
}
