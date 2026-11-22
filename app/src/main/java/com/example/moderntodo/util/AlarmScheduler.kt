package com.example.moderntodo.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.receiver.AlarmReceiver
import com.example.moderntodo.repository.TaskRepository
import java.util.Calendar
import java.util.Locale

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

        val now = System.currentTimeMillis()
        val triggerTime = computeTriggerTime(task, fromTime = now)
        if (triggerTime == null) {
            Log.d(TAG, "Task '${task.title}' has no future scheduled time or date. Skipping.")
            return
        }

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

        // Schedule exact alarm for the deadline / repeat occurrence
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

    fun computeTriggerTime(task: TaskEntity, fromTime: Long = System.currentTimeMillis()): Long? {
        val isRepeating = !task.repeatMode.isNullOrBlank() && task.repeatMode != "None" && task.repeatMode != "Off"

        // 1. If explicit time is set
        if (task.dueTime != null) {
            if (task.dueTime > fromTime) {
                return task.dueTime
            }
            if (isRepeating) {
                return computeNextRepeatTime(task, task.dueTime, fromTime)
            }
            return null
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
            if (trigger > fromTime) {
                return trigger
            }

            if (isRepeating) {
                return computeNextRepeatTime(task, trigger, fromTime)
            }

            // If the date is today and default time has passed, trigger 1 minute from now
            val todayCal = Calendar.getInstance().apply { timeInMillis = fromTime }
            val isToday = todayCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    todayCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

            if (isToday) {
                return fromTime + 60 * 1000L
            }

            return null
        }

        // 3. If neither is set, but hasAlarm is true, schedule reminder 1 minute ahead
        if (task.hasAlarm) {
            return fromTime + 60 * 1000L
        }

        return null
    }

    fun computeNextRepeatTime(task: TaskEntity, baseTime: Long, fromTime: Long): Long? {
        val interval = if (task.repeatInterval <= 0) 1 else task.repeatInterval
        val unit = if (task.repeatUnit.isNotBlank()) task.repeatUnit else when (task.repeatMode) {
            "Hourly" -> "Hours"
            "Weekly" -> "Weeks"
            "Monthly" -> "Months"
            "Yearly" -> "Years"
            else -> "Days"
        }
        val daysOfWeek = task.repeatDaysOfWeek?.split(",")?.map { it.trim().lowercase(Locale.ROOT) }?.filter { it.isNotBlank() }

        val cal = Calendar.getInstance().apply { timeInMillis = baseTime }

        fun advanceStep() {
            if (!daysOfWeek.isNullOrEmpty()) {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (!isDayOfWeekMatched(cal.get(Calendar.DAY_OF_WEEK), daysOfWeek))
            } else {
                when (unit.lowercase(Locale.ROOT)) {
                    "hours", "hour" -> cal.add(Calendar.HOUR_OF_DAY, interval)
                    "weeks", "week" -> cal.add(Calendar.WEEK_OF_YEAR, interval)
                    "months", "month" -> cal.add(Calendar.MONTH, interval)
                    "years", "year" -> cal.add(Calendar.YEAR, interval)
                    else -> cal.add(Calendar.DAY_OF_YEAR, interval)
                }
            }
        }

        // Always advance AT LEAST once from baseTime
        advanceStep()

        var count = 0
        while (cal.timeInMillis <= fromTime && count < 500) {
            count++
            advanceStep()
        }

        val nextTime = cal.timeInMillis

        // Verify Ends condition
        if (task.repeatEndsType == "OnDate" && task.repeatEndsValue != null) {
            if (nextTime > task.repeatEndsValue) {
                return null
            }
        } else if (task.repeatEndsType == "After" && task.repeatEndsValue != null) {
            if (task.repeatEndsValue <= 0) {
                return null
            }
        }

        return nextTime
    }

    private fun isDayOfWeekMatched(calDayOfWeek: Int, daysOfWeek: List<String>): Boolean {
        val code = when (calDayOfWeek) {
            Calendar.SUNDAY -> "sun"
            Calendar.MONDAY -> "mon"
            Calendar.TUESDAY -> "tue"
            Calendar.WEDNESDAY -> "wed"
            Calendar.THURSDAY -> "thu"
            Calendar.FRIDAY -> "fri"
            Calendar.SATURDAY -> "sat"
            else -> ""
        }
        return daysOfWeek.any { it.startsWith(code) }
    }

    suspend fun rescheduleNextRepeat(context: Context, repo: TaskRepository, task: TaskEntity) {
        val now = System.currentTimeMillis()
        val baseTime = task.dueTime ?: task.dueDate ?: now
        val nextTrigger = computeNextRepeatTime(task, baseTime, now)

        if (nextTrigger == null) {
            Log.d(TAG, "Task '${task.title}' reached repeat end. Stopping recurrence.")
            repo.updateTask(task.copy(repeatMode = null))
            cancel(context, task.id ?: 0)
            return
        }

        val newDueDate = if (task.dueDate != null) nextTrigger else null
        val newDueTime = if (task.dueTime != null) nextTrigger else null
        val newEndsValue = if (task.repeatEndsType == "After" && task.repeatEndsValue != null) {
            val v = task.repeatEndsValue - 1
            if (v <= 0) null else v
        } else task.repeatEndsValue
        val newRepeatMode = if (task.repeatEndsType == "After" && newEndsValue == null) null else task.repeatMode

        val updatedTask = task.copy(
            dueDate = newDueDate,
            dueTime = newDueTime,
            repeatEndsValue = newEndsValue,
            repeatMode = newRepeatMode
        )

        repo.updateTask(updatedTask)
        Log.d(TAG, "Rescheduling repeating task '${task.title}' for next occurrence at $nextTrigger")
        schedule(context, updatedTask)

        try {
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                context,
                repo,
                updatedTask.projectId ?: updatedTask.bookId
            )
        } catch (_: Exception) {}
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
