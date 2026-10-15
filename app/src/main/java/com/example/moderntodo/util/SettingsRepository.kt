package com.example.moderntodo.util

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsRepository {
    private lateinit var prefs: SharedPreferences

    private val _themeFlow = MutableStateFlow("System")
    val themeFlow: StateFlow<String> = _themeFlow

    private val _defaultReminderTimeFlow = MutableStateFlow(9L * 60 * 60 * 1000) // Default 9 AM in ms from midnight
    val defaultReminderTimeFlow: StateFlow<Long> = _defaultReminderTimeFlow
    
    private val _notificationsEnabledFlow = MutableStateFlow(true)
    val notificationsEnabledFlow: StateFlow<Boolean> = _notificationsEnabledFlow
    
    private val _defaultRingtoneUriFlow = MutableStateFlow<String?>(null)
    val defaultRingtoneUriFlow: StateFlow<String?> = _defaultRingtoneUriFlow

    private val _accessTokenFlow = MutableStateFlow<String?>(null)
    val accessTokenFlow: StateFlow<String?> = _accessTokenFlow.asStateFlow()

    private val _refreshTokenFlow = MutableStateFlow<String?>(null)
    val refreshTokenFlow: StateFlow<String?> = _refreshTokenFlow.asStateFlow()

    private val _userEmailFlow = MutableStateFlow<String?>(null)
    val userEmailFlow: StateFlow<String?> = _userEmailFlow.asStateFlow()

    private val _userNameFlow = MutableStateFlow<String?>(null)
    val userNameFlow: StateFlow<String?> = _userNameFlow.asStateFlow()

    private val _userPhotoUriFlow = MutableStateFlow<String?>(null)
    val userPhotoUriFlow: StateFlow<String?> = _userPhotoUriFlow.asStateFlow()

    fun setTokens(token: String?, refreshToken: String? = null) {
        _accessTokenFlow.value = token
        _refreshTokenFlow.value = refreshToken
        prefs.edit()
            .putString("access_token", token)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun setAccessToken(token: String?) {
        setTokens(token, null)
    }

    fun updateUserProfile(context: Context, email: String?, name: String?, photoUri: String? = null) {
        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        val editor = userPrefs.edit()
        if (!email.isNullOrBlank()) {
            editor.putString("email", email)
            _userEmailFlow.value = email
        }
        if (!name.isNullOrBlank()) {
            editor.putString("name", name)
            editor.putString("username", "@" + name.lowercase().replace(" ", ""))
            _userNameFlow.value = name
        }
        if (!photoUri.isNullOrBlank()) {
            editor.putString("imageUri", photoUri)
            _userPhotoUriFlow.value = photoUri
        }
        editor.apply()
    }

    fun logout(context: Context, onComplete: () -> Unit = {}) {
        // 1. Clear session tokens
        setTokens(null, null)

        // 2. Clear user_profile SharedPreferences
        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        userPrefs.edit().clear().apply()
        _userEmailFlow.value = null
        _userNameFlow.value = null
        _userPhotoUriFlow.value = null

        // 3. Clear Google Sign-In client state so user is prompted to choose an account next time
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(com.example.moderntodo.data.remote.SupabaseClient.GOOGLE_CLIENT_ID)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                client.revokeAccess().addOnCompleteListener {
                    onComplete()
                }
            }
        } catch (e: Exception) {
            onComplete()
        }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        _themeFlow.value = prefs.getString("theme_mode", "System") ?: "System"
        _defaultReminderTimeFlow.value = prefs.getLong("default_reminder_time", 9L * 60 * 60 * 1000)
        _notificationsEnabledFlow.value = prefs.getBoolean("notifications_enabled", true)
        _defaultRingtoneUriFlow.value = prefs.getString("default_ringtone_uri", null)
        _accessTokenFlow.value = prefs.getString("access_token", null)
        _refreshTokenFlow.value = prefs.getString("refresh_token", null)

        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        _userEmailFlow.value = userPrefs.getString("email", null)
        _userNameFlow.value = userPrefs.getString("name", null)
        _userPhotoUriFlow.value = userPrefs.getString("imageUri", null)
    }

    fun setTheme(theme: String) {
        prefs.edit().putString("theme_mode", theme).apply()
        _themeFlow.value = theme
    }

    fun setDefaultReminderTime(timeMs: Long) {
        prefs.edit().putLong("default_reminder_time", timeMs).apply()
        _defaultReminderTimeFlow.value = timeMs
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _notificationsEnabledFlow.value = enabled
    }
    
    fun setDefaultRingtoneUri(uri: String?) {
        prefs.edit().putString("default_ringtone_uri", uri).apply()
        _defaultRingtoneUriFlow.value = uri
    }
}
