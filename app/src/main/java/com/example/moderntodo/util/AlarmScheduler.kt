package com.example.moderntodo.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.receiver.AlarmReceiver

object AlarmScheduler {
    fun schedule(context: Context, task: TaskEntity) {
        if (!com.example.moderntodo.util.SettingsRepository.notificationsEnabledFlow.value) {
            cancel(context, task.id ?: 0)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = task.dueTime ?: (task.dueDate?.plus(9 * 60 * 60 * 1000) ?: return)

        if (triggerTime < System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_TITLE", task.title)
            putExtra("EXTRA_DESC", task.description)
            putExtra("EXTRA_TASK_ID", task.id)
            putExtra("EXTRA_RINGTONE_URI", task.ringtoneUri)
            putExtra("EXTRA_HAS_ALARM", task.hasAlarm)
        }

        val offsets = listOf<Long>(
            0L, // Exact deadline
            -8 * 3600000L, -16 * 3600000L, -24 * 3600000L, // Orange (3 times in last 24h)
            4 * 3600000L, 8 * 3600000L, 12 * 3600000L, 16 * 3600000L, 20 * 3600000L, 24 * 3600000L // Red (6 times in 24h after overdue)
        )

        for ((index, offset) in offsets.withIndex()) {
            val alarmTime = triggerTime + offset
            if (alarmTime < System.currentTimeMillis()) continue

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (task.id ?: 0) * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarmTime, pendingIntent), pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                }
            } else {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarmTime, pendingIntent), pendingIntent)
            }
        }
    }

    fun cancel(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        for (index in 0..20) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
