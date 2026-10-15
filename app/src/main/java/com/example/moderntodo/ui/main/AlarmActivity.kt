package com.example.moderntodo.ui.main

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moderntodo.ui.theme.ModernToDoTheme

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure screen turns on and bypasses keyguard
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Task Alarm"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: ""
        val ringtoneUriStr = intent.getStringExtra("EXTRA_RINGTONE_URI")
        
        val uri = if (ringtoneUriStr != null) Uri.parse(ringtoneUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        ringtone?.play()

        setContent {
            ModernToDoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ALARM", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(desc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        
                        Spacer(modifier = Modifier.height(64.dp))
                        Button(
                            onClick = {
                                ringtone?.stop()
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text("DISMISS ALARM", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}
