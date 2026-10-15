package com.example.moderntodo.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.moderntodo.MainActivity
import com.example.moderntodo.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!com.example.moderntodo.util.SettingsRepository.notificationsEnabledFlow.value) return
        
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Task Reminder"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "You have a task due now."
        val taskId = intent.getIntExtra("EXTRA_TASK_ID", (System.currentTimeMillis() % 100000).toInt())
        val ringtoneUriStr = intent.getStringExtra("EXTRA_RINGTONE_URI")
        val hasAlarm = intent.getBooleanExtra("EXTRA_HAS_ALARM", false)

        showNotification(context, taskId, title, desc, ringtoneUriStr, hasAlarm)
    }

    companion object {
        fun showNotification(
            context: Context,
            taskId: Int,
            title: String,
            desc: String,
            ringtoneUriStr: String?,
            hasAlarm: Boolean
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val customUri = if (!ringtoneUriStr.isNullOrBlank()) {
                try { Uri.parse(ringtoneUriStr) } catch (_: Exception) { null }
            } else null

            val soundUri: Uri = customUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Distinct notification channel ID per custom sound so Android 8.0+ applies the specific audio
            val channelId = if (customUri != null) {
                "task_sound_" + customUri.toString().hashCode().toString().replace("-", "n")
            } else {
                "task_sound_default"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundTitle = try {
                    RingtoneManager.getRingtone(context, soundUri)?.getTitle(context) ?: "Custom Sound"
                } catch (_: Exception) {
                    "Custom Sound"
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    if (customUri != null) "Task Alert ($soundTitle)" else "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for tasks with custom sound alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Tapping notification opens MainActivity
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(desc)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(contentPendingIntent)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 350, 150, 350))

            if (hasAlarm) {
                val activityIntent = Intent(context, com.example.moderntodo.ui.main.AlarmActivity::class.java).apply {
                    putExtra("EXTRA_TITLE", title)
                    putExtra("EXTRA_DESC", desc)
                    putExtra("EXTRA_RINGTONE_URI", ringtoneUriStr)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val alarmPendingIntent = PendingIntent.getActivity(
                    context,
                    taskId + 100000,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setFullScreenIntent(alarmPendingIntent, true)
            } else {
                // If it's a notification, play the custom ringtone once to guarantee audibility
                try {
                    val ringtone = RingtoneManager.getRingtone(context, soundUri)
                    ringtone?.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            notificationManager.notify(taskId, builder.build())
        }
    }
}
