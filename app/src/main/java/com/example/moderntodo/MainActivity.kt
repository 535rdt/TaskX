package com.example.moderntodo

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.moderntodo.ui.main.MainScreen
import com.example.moderntodo.ui.theme.ModernToDoTheme
import androidx.compose.runtime.collectAsState
import com.example.moderntodo.util.SettingsRepository
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        
        enableEdgeToEdge()
        setContent {
            val themeState = SettingsRepository.themeFlow.collectAsState(initial = "System")
            val isDark = when(themeState.value) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            ModernToDoTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val uriStr = uri.toString()
        if (uriStr.contains("login-callback") || uriStr.contains("localhost")) {
            val fragmentOrQuery = when {
                uriStr.contains("#") -> uriStr.substringAfter("#")
                uriStr.contains("?") -> uriStr.substringAfter("?")
                else -> ""
            }
            if (fragmentOrQuery.isNotEmpty()) {
                val params = fragmentOrQuery.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                }
                val accessToken = params["access_token"]
                val refreshToken = params["refresh_token"]
                if (!accessToken.isNullOrBlank()) {
                    SettingsRepository.setTokens(accessToken, refreshToken)
                    try {
                        val parts = accessToken.split(".")
                        if (parts.size >= 2) {
                            val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                            val decoded = String(decodedBytes, Charsets.UTF_8)
                            val json = JSONObject(decoded)
                            val email = json.optString("email", "")
                            val userMetadata = json.optJSONObject("user_metadata")
                            val name = userMetadata?.optString("full_name", "")?.takeIf { it.isNotBlank() }
                                ?: userMetadata?.optString("name", "")?.takeIf { it.isNotBlank() }
                                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                            val avatarUrl = userMetadata?.optString("avatar_url", "")?.takeIf { it.isNotBlank() }
                            SettingsRepository.updateUserProfile(
                                context = this,
                                email = email.ifBlank { null },
                                name = name.ifBlank { null },
                                photoUri = avatarUrl
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    android.widget.Toast.makeText(this, "Signed in successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val errorDesc = params["error_description"] ?: params["error"]
                    if (!errorDesc.isNullOrBlank()) {
                        android.widget.Toast.makeText(this, errorDesc, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
