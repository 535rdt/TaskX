package com.example.moderntodo.ui.main

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moderntodo.util.SettingsRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val themeMode by SettingsRepository.themeFlow.collectAsState(initial = "System")
    val notificationsEnabled by SettingsRepository.notificationsEnabledFlow.collectAsState(initial = true)
    val defaultTime by SettingsRepository.defaultReminderTimeFlow.collectAsState(initial = 32400000L) // 9:00 AM
    val currentEmail by SettingsRepository.userEmailFlow.collectAsState()
    val currentName by SettingsRepository.userNameFlow.collectAsState()
    val displayEmail = currentEmail ?: "Logged In"
    val displayName = currentName ?: "User"

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Section (Account, General, Subscription, Calendar)
            SettingsItem(
                icon = Icons.Outlined.AccountCircle,
                title = "Account",
                onClick = { showAccountDialog = true }
            )
            SettingsItem(
                icon = Icons.Outlined.Settings,
                title = "General",
                onClick = { Toast.makeText(context, "General settings", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.CreditCard,
                title = "Subscription",
                onClick = { Toast.makeText(context, "Current Plan: Free Tier", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.CalendarToday,
                title = "Calendar",
                onClick = { Toast.makeText(context, "Calendar integration", Toast.LENGTH_SHORT).show() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

            // Personalization Section
            SettingsCategory(title = "Personalization")
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "Theme",
                subtitle = themeMode,
                onClick = { showThemeDialog = true }
            )
            SettingsItem(
                icon = Icons.Outlined.Checklist,
                title = "App icon",
                subtitle = "Todoist",
                onClick = { Toast.makeText(context, "App icon: Todoist", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.Smartphone,
                title = "Navigation bar",
                onClick = { Toast.makeText(context, "Navigation bar customized", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.Menu,
                title = "Navigation menu",
                onClick = { Toast.makeText(context, "Navigation menu settings", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.AddBox,
                title = "Quick Add",
                onClick = { Toast.makeText(context, "Quick Add enabled", Toast.LENGTH_SHORT).show() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

            // Productivity Section
            SettingsCategory(title = "Productivity")
            SettingsItem(
                icon = Icons.Outlined.TrendingUp,
                title = "Productivity",
                onClick = { Toast.makeText(context, "View Productivity Trends", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.Alarm,
                title = "Reminders",
                subtitle = run {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = 0
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        add(Calendar.MILLISECOND, defaultTime.toInt())
                    }
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
                },
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = 0
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        add(Calendar.MILLISECOND, defaultTime.toInt())
                    }
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m ->
                            SettingsRepository.setDefaultReminderTime((h * 3600000L) + (m * 60000L))
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                }
            )
            SettingsItem(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                subtitle = if (notificationsEnabled) "Enabled" else "Disabled",
                onClick = {
                    val newState = !notificationsEnabled
                    SettingsRepository.setNotificationsEnabled(newState)
                    Toast.makeText(context, if (newState) "Notifications turned on" else "Notifications turned off", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

            // More Section
            SettingsCategory(title = "More")
            SettingsItem(
                icon = Icons.Outlined.HelpOutline,
                title = "Help & resources",
                onClick = { Toast.makeText(context, "Modern ToDo Help Center", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "About",
                subtitle = "Version 1.0",
                onClick = { showAboutDialog = true }
            )
            SettingsItem(
                icon = Icons.Outlined.CardGiftcard,
                title = "What's new",
                onClick = { Toast.makeText(context, "You're on the latest version!", Toast.LENGTH_SHORT).show() }
            )
            SettingsItem(
                icon = Icons.Outlined.ExitToApp,
                title = "Log out",
                subtitle = displayEmail,
                onClick = { showLogoutDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    listOf("System", "Light", "Dark").forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SettingsRepository.setTheme(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    SettingsRepository.setTheme(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Account Dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account Details") },
            text = {
                Column {
                    Text("User: $displayName", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Email: $displayEmail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sync: Supabase Cloud Active", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About DO-IT") },
            text = {
                Column {
                    Text("Modern ToDo App v1.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Built with Jetpack Compose & Supabase.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Log out Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("Are you sure you want to log out from $displayEmail?") },
            confirmButton = {
                Button(
                    onClick = {
                        SettingsRepository.logout(context) {
                            showLogoutDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        color = Color(0xFFE53935),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
