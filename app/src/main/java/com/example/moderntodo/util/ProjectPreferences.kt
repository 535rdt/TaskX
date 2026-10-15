package com.example.moderntodo.util

import android.content.Context
import android.content.SharedPreferences

object ProjectPreferences {
    val COLOR_PALETTE = listOf(
        "#2563EB", // Blue
        "#10B981", // Emerald Green
        "#F59E0B", // Amber
        "#EF4444", // Red
        "#8B5CF6", // Purple
        "#3F51B5", // Indigo
        "#06B6D4", // Cyan
        "#EC4899"  // Pink
    )

    val ICONS = listOf(
        "folder",
        "work",
        "code",
        "rocket",
        "study",
        "star",
        "fitness",
        "shopping",
        "home"
    )

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs: SharedPreferences?
        get() {
            if (!::appContext.isInitialized) return null
            val email = SettingsRepository.userEmailFlow.value
            val sanitized = if (!email.isNullOrBlank()) {
                email.lowercase().trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
            } else "guest"
            val prefName = "project_preferences_$sanitized"
            val targetPrefs = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)

            // Legacy migration for original user if empty
            if (targetPrefs.all.isEmpty() && email?.contains("muhammadirfannkm342", ignoreCase = true) == true) {
                val legacyPrefs = appContext.getSharedPreferences("project_preferences", Context.MODE_PRIVATE)
                if (legacyPrefs.all.isNotEmpty()) {
                    val editor = targetPrefs.edit()
                    legacyPrefs.all.forEach { (k, v) ->
                        when (v) {
                            is String -> editor.putString(k, v)
                            is Boolean -> editor.putBoolean(k, v)
                            is Int -> editor.putInt(k, v)
                            is Long -> editor.putLong(k, v)
                            is Float -> editor.putFloat(k, v)
                        }
                    }
                    editor.apply()
                }
            }
            return targetPrefs
        }

    fun getColorHex(bookId: Int): String {
        val p = prefs ?: return COLOR_PALETTE[Math.abs(bookId) % COLOR_PALETTE.size]
        return p.getString("proj_${bookId}_color", null)
            ?: COLOR_PALETTE[Math.abs(bookId) % COLOR_PALETTE.size]
    }

    fun setColorHex(bookId: Int, hex: String) {
        prefs?.edit()?.putString("proj_${bookId}_color", hex)?.apply()
    }

    fun getIconName(bookId: Int): String {
        val p = prefs ?: return "folder"
        return p.getString("proj_${bookId}_icon", "folder") ?: "folder"
    }

    fun setIconName(bookId: Int, icon: String) {
        prefs?.edit()?.putString("proj_${bookId}_icon", icon)?.apply()
    }

    fun getDescription(bookId: Int): String {
        val p = prefs ?: return ""
        return p.getString("proj_${bookId}_desc", "") ?: ""
    }

    fun setDescription(bookId: Int, desc: String) {
        prefs?.edit()?.putString("proj_${bookId}_desc", desc)?.apply()
    }

    fun getDeadline(bookId: Int): Long? {
        val p = prefs ?: return null
        val time = p.getLong("proj_${bookId}_deadline", -1L)
        return if (time > 0L) time else null
    }

    fun setDeadline(bookId: Int, deadline: Long?) {
        val p = prefs ?: return
        if (deadline != null && deadline > 0) {
            p.edit().putLong("proj_${bookId}_deadline", deadline).apply()
        } else {
            p.edit().remove("proj_${bookId}_deadline").apply()
        }
    }

    fun getCategory(bookId: Int): String {
        val p = prefs ?: return "General"
        return p.getString("proj_${bookId}_cat", "General") ?: "General"
    }

    fun setCategory(bookId: Int, category: String) {
        prefs?.edit()?.putString("proj_${bookId}_cat", category)?.apply()
    }

    fun setProjectMetadata(
        bookId: Int,
        colorHex: String? = null,
        iconName: String? = null,
        description: String? = null,
        deadline: Long? = null,
        category: String? = null
    ) {
        if (colorHex != null) setColorHex(bookId, colorHex)
        if (iconName != null) setIconName(bookId, iconName)
        if (description != null) setDescription(bookId, description)
        if (deadline != null) setDeadline(bookId, deadline)
        if (category != null) setCategory(bookId, category)
        setIsProject(bookId, true)
    }

    fun isProject(bookId: Int): Boolean {
        if (bookId == 1) return false
        val p = prefs ?: return false
        return p.getBoolean("proj_${bookId}_is_proj", false) || hasProjectMetadata(bookId)
    }

    fun setIsProject(bookId: Int, isProj: Boolean) {
        prefs?.edit()?.putBoolean("proj_${bookId}_is_proj", isProj)?.apply()
    }

    fun hasProjectMetadata(bookId: Int): Boolean {
        if (bookId == 1) return false
        val p = prefs ?: return false
        return p.contains("proj_${bookId}_color") ||
               p.contains("proj_${bookId}_desc") ||
               p.contains("proj_${bookId}_deadline") ||
               p.contains("proj_${bookId}_cat")
    }
}
