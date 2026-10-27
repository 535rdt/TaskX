package com.example.moderntodo.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.moderntodo.MainActivity
import com.example.moderntodo.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm broadcast received!")
        if (!com.example.moderntodo.util.SettingsRepository.notificationsEnabledFlow.value) {
            Log.d("AlarmReceiver", "Notifications disabled in app settings. Skipping.")
            return
        }

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Task Reminder"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "You have a task due now."
        val taskId = intent.getIntExtra("EXTRA_TASK_ID", (System.currentTimeMillis() % 100000).toInt())
        val ringtoneUriStr = intent.getStringExtra("EXTRA_RINGTONE_URI")
        val hasAlarm = intent.getBooleanExtra("EXTRA_HAS_ALARM", false)

        showNotification(context, taskId, title, desc, ringtoneUriStr, hasAlarm)
    }

    companion object {
        private const val CHANNEL_ID_DEFAULT = "task_reminders_channel_v3"
        private const val CHANNEL_NAME = "Task Reminders & Alarms"

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

            val channelId = if (customUri != null) {
                "task_sound_" + customUri.toString().hashCode().toString().replace("-", "n")
            } else {
                CHANNEL_ID_DEFAULT
            }

            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundTitle = try {
                    RingtoneManager.getRingtone(context, soundUri)?.getTitle(context) ?: "Custom Sound"
                } catch (_: Exception) {
                    "Custom Sound"
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(if (hasAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    if (customUri != null) "Task Alert ($soundTitle)" else CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High priority notifications for due tasks and alarms"
                    enableLights(true)
                    lightColor = Color.CYAN
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Tapping notification opens MainActivity
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("FROM_NOTIFICATION_TASK_ID", taskId)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct vibration feedback so user notices immediately
            triggerDirectVibration(context)

            // Direct sound play to guarantee audibility
            triggerDirectSound(context, soundUri)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_task_notification)
                .setContentTitle(title)
                .setContentText(desc)
                .setStyle(NotificationCompat.BigTextStyle().bigText(desc))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(if (hasAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentPendingIntent)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

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
            }

            try {
                notificationManager.notify(taskId, builder.build())
                Log.d("AlarmReceiver", "Notification posted successfully for task $taskId")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to post notification for task $taskId", e)
            }
        }

        private fun triggerDirectVibration(context: Context) {
            try {
                val pattern = longArrayOf(0, 400, 200, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                }
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Vibration failed", e)
            }
        }

        private fun triggerDirectSound(context: Context, soundUri: Uri) {
            try {
                val ringtone = RingtoneManager.getRingtone(context, soundUri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Direct sound playback failed", e)
            }
        }
    }
}
