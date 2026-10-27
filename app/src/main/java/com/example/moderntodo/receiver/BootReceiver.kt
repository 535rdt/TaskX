package com.example.moderntodo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.moderntodo.ModernToDoApp
import com.example.moderntodo.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d("BootReceiver", "Device reboot detected. Restoring task alarms...")
            val app = context.applicationContext as? ModernToDoApp ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = app.repository
                    val tasks = repo.getAllTasksList()
                    val activeTasks = tasks.filter { !it.isCompleted && (it.dueTime != null || it.dueDate != null || it.hasAlarm) }
                    Log.d("BootReceiver", "Rescheduling ${activeTasks.size} pending task alarms...")
                    activeTasks.forEach { task ->
                        AlarmScheduler.schedule(context, task)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to restore alarms after reboot", e)
                }
            }
        }
    }
}
