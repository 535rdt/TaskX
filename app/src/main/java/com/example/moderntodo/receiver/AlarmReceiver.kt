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
import com.example.moderntodo.ModernToDoApp
import com.example.moderntodo.R
import com.example.moderntodo.util.AlarmScheduler
import com.example.moderntodo.util.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm broadcast received!")
        if (!SettingsRepository.notificationsEnabledFlow.value) {
            Log.d("AlarmReceiver", "Notifications disabled in app settings. Skipping.")
            return
        }

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Task Reminder"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "You have a task due now."
        val taskId = intent.getIntExtra("EXTRA_TASK_ID", (System.currentTimeMillis() % 100000).toInt())
        val ringtoneUriStr = intent.getStringExtra("EXTRA_RINGTONE_URI")
        val hasAlarm = intent.getBooleanExtra("EXTRA_HAS_ALARM", false)

        showNotification(context, taskId, title, desc, ringtoneUriStr, hasAlarm)

        if (taskId > 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? ModernToDoApp
                    if (app != null) {
                        val repo = app.repository
                        val task = repo.getTaskById(taskId)
                        if (task != null && !task.isCompleted && !task.repeatMode.isNullOrBlank() && task.repeatMode != "None" && task.repeatMode != "Off") {
                            AlarmScheduler.rescheduleNextRepeat(context, repo, task)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Failed to reschedule repeating task $taskId", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID_NOTIFICATION = "task_notifications_channel_v1"
        private const val CHANNEL_ID_ALARM = "task_full_alarm_channel_v1"

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

            val defaultUriStr = if (hasAlarm) {
                SettingsRepository.defaultAlarmRingtoneUriFlow.value
            } else {
                SettingsRepository.defaultRingtoneUriFlow.value
            }
            val defaultUri = if (!defaultUriStr.isNullOrBlank()) {
                try { Uri.parse(defaultUriStr) } catch (_: Exception) { null }
            } else null

            // When Full Alarm is ON: fallback to alarm sound; when OFF: fallback to notification sound
            val soundUri: Uri = customUri ?: defaultUri ?: RingtoneManager.getDefaultUri(
                if (hasAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            )

            val channelId = if (customUri != null) {
                "task_sound_" + customUri.toString().hashCode().toString().replace("-", "n") + if (hasAlarm) "_alarm" else "_notif"
            } else if (hasAlarm) {
                CHANNEL_ID_ALARM
            } else {
                CHANNEL_ID_NOTIFICATION
            }

            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundTitle = try {
                    RingtoneManager.getRingtone(context, soundUri)?.getTitle(context) ?: if (hasAlarm) "Full Alarm" else "Notification"
                } catch (_: Exception) {
                    if (hasAlarm) "Full Alarm" else "Notification"
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(if (hasAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build()

                val channelName = if (customUri != null) {
                    if (hasAlarm) "Task Alarm ($soundTitle)" else "Task Notification ($soundTitle)"
                } else {
                    if (hasAlarm) "Task Alarms (Full Alarm)" else "Task Notifications"
                }

                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (hasAlarm) "High priority ringing alarms for tasks" else "Standard notifications for due tasks"
                    enableLights(true)
                    lightColor = if (hasAlarm) Color.RED else Color.CYAN
                    enableVibration(true)
                    vibrationPattern = if (hasAlarm) longArrayOf(0, 500, 200, 500, 200, 500) else longArrayOf(0, 400, 200, 400)
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
            triggerDirectVibration(context, hasAlarm)

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
                .setVibrate(if (hasAlarm) longArrayOf(0, 500, 200, 500, 200, 500) else longArrayOf(0, 400, 200, 400))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (hasAlarm) {
                // When Full Alarm is ON: Ring full alarm and pop up AlarmActivity
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

                try {
                    context.startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.w("AlarmReceiver", "Direct launch of AlarmActivity failed, falling back to full-screen intent", e)
                }
            } else {
                // When Full Alarm is OFF: Only notification works
                triggerDirectSound(context, soundUri)
            }

            try {
                notificationManager.notify(taskId, builder.build())
                Log.d("AlarmReceiver", "Notification posted successfully for task $taskId (full alarm: $hasAlarm)")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to post notification for task $taskId", e)
            }
        }

        private fun triggerDirectVibration(context: Context, hasAlarm: Boolean = false) {
            try {
                val pattern = if (hasAlarm) longArrayOf(0, 500, 200, 500, 200, 500) else longArrayOf(0, 400, 200, 400)
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
