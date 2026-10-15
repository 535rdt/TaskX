package com.example.moderntodo.ui.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.PriorityLevel
import com.example.moderntodo.util.SettingsRepository
import com.example.moderntodo.viewmodel.TaskViewModel
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.*

data class ProfileAchievement(
    val id: String,
    val title: String,
    val requirement: String,
    val icon: ImageVector,
    val current: Int,
    val target: Int,
    val isUnlocked: Boolean,
    val accentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_profile", Context.MODE_PRIVATE) }

    val currentSavedEmail by SettingsRepository.userEmailFlow.collectAsState()
    val currentSavedName by SettingsRepository.userNameFlow.collectAsState()
    val currentSavedPhoto by SettingsRepository.userPhotoUriFlow.collectAsState()

    var name by remember(currentSavedName) { mutableStateOf(currentSavedName ?: prefs.getString("name", "User") ?: "User") }
    var username by remember(name) { mutableStateOf(prefs.getString("username", "@" + name.lowercase().replace(" ", "")) ?: "@user") }
    var title by remember { mutableStateOf(prefs.getString("title", "Productivity Architect") ?: "Productivity Architect") }
    var email by remember(currentSavedEmail) { mutableStateOf(currentSavedEmail ?: prefs.getString("email", "") ?: "") }
    var bio by remember { mutableStateOf(prefs.getString("bio", "Executing high-impact goals daily and keeping life in perfect flow.") ?: "Executing high-impact goals daily and keeping life in perfect flow.") }
    var dailyGoal by remember { mutableIntStateOf(prefs.getInt("daily_goal", 5)) }
    var imageUri by remember(currentSavedPhoto) { mutableStateOf(currentSavedPhoto ?: prefs.getString("imageUri", null)) }

    var showEditSheet by remember { mutableStateOf(false) }

    val allTasks by viewModel.allTasks.collectAsState()
    val allPendingTasks by viewModel.allPendingTasks.collectAsState()
    val taskBooks by viewModel.taskBooks.collectAsState()

    val completedTasks = remember(allTasks) { allTasks.filter { it.isCompleted } }
    val completedCount = completedTasks.size
    val pendingCount = allPendingTasks.size
    val totalTasks = allTasks.size
    val p1CompletedCount = completedTasks.count { it.priority == PriorityLevel.URGENT.level || it.priority == 1 }
    val projectCount = taskBooks.count { it.isProject }
    val now = System.currentTimeMillis()
    val overdueCount = allTasks.count { !it.isCompleted && ((it.dueTime ?: it.dueDate ?: Long.MAX_VALUE) < now) }

    // Productivity Karma Score & Tier Progression
    val karmaScore = remember(completedCount, p1CompletedCount, projectCount) {
        1250 + (completedCount * 35) + (p1CompletedCount * 50) + (projectCount * 75)
    }

    val (karmaTier, nextTierThreshold, tierColor) = when {
        karmaScore >= 7500 -> Triple("Grandmaster", 10000, Color(0xFF10B981))
        karmaScore >= 5000 -> Triple("Master", 7500, Color(0xFF6366F1))
        karmaScore >= 3000 -> Triple("Expert", 5000, Color(0xFFF59E0B))
        karmaScore >= 1800 -> Triple("Professional", 3000, Color(0xFF3B82F6))
        else -> Triple("Novice", 1800, Color(0xFF8B5CF6))
    }

    val karmaProgress = (karmaScore.toFloat() / nextTierThreshold).coerceIn(0f, 1f)
    val animatedKarmaProgress by animateFloatAsState(targetValue = karmaProgress, animationSpec = tween(800), label = "karma")

    // Image Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
            prefs.edit().putString("imageUri", imageUri).apply()
            Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
        }
    }

    // Achievements calculation
    val achievements = remember(completedCount, p1CompletedCount, projectCount, overdueCount, totalTasks) {
        listOf(
            ProfileAchievement(
                id = "first_task",
                title = "First Victory",
                requirement = "Complete your first task",
                icon = Icons.Outlined.CheckCircle,
                current = completedCount.coerceAtMost(1),
                target = 1,
                isUnlocked = completedCount >= 1,
                accentColor = Color(0xFF10B981)
            ),
            ProfileAchievement(
                id = "p1_slayer",
                title = "P1 Crusher",
                requirement = "Complete 3 urgent (P1) tasks",
                icon = Icons.Filled.ElectricBolt,
                current = p1CompletedCount.coerceAtMost(3),
                target = 3,
                isUnlocked = p1CompletedCount >= 3,
                accentColor = Color(0xFFEF4444)
            ),
            ProfileAchievement(
                id = "task_titan",
                title = "Task Titan",
                requirement = "Complete 15 total tasks",
                icon = Icons.Filled.EmojiEvents,
                current = completedCount.coerceAtMost(15),
                target = 15,
                isUnlocked = completedCount >= 15,
                accentColor = Color(0xFFF59E0B)
            ),
            ProfileAchievement(
                id = "project_architect",
                title = "Project Lead",
                requirement = "Create & manage 2+ projects",
                icon = Icons.Outlined.FolderSpecial,
                current = projectCount.coerceAtMost(2),
                target = 2,
                isUnlocked = projectCount >= 2,
                accentColor = Color(0xFF8B5CF6)
            ),
            ProfileAchievement(
                id = "deep_focus",
                title = "Focus Master",
                requirement = "Complete 4 deep work sessions",
                icon = Icons.Outlined.Timer,
                current = 3.coerceAtMost(4), // estimated focus sessions
                target = 4,
                isUnlocked = completedCount >= 4,
                accentColor = Color(0xFF06B6D4)
            ),
            ProfileAchievement(
                id = "clean_slate",
                title = "Clean Pipeline",
                requirement = "Zero overdue tasks pending",
                icon = Icons.Outlined.Shield,
                current = if (overdueCount == 0 && totalTasks > 0) 1 else 0,
                target = 1,
                isUnlocked = overdueCount == 0 && totalTasks > 0,
                accentColor = Color(0xFF14B8A6)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Executive Profile", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // -------------------------------------------------------------
            // 1. Executive Hero Identity Card
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar with Edit Badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(108.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(imageUri),
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = name.firstOrNull()?.uppercase() ?: "I",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Camera / Edit Badge Chip
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { imagePickerLauncher.launch("image/*") }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = "Change photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Full Name + Verified Pro Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Verified Pro",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Username & Email
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Headline & Bio
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"$bio\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick "Edit Profile" Button
                        OutlinedButton(
                            onClick = { showEditSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile Details")
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. Productivity Karma & Progression Bar
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.08f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(tierColor.copy(alpha = 0.4f), tierColor)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRODUCTIVITY KARMA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tierColor,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = karmaTier,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tierColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "$karmaScore PTS",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = tierColor,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Linear Karma Progress Bar
                        LinearProgressIndicator(
                            progress = { animatedKarmaProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = tierColor,
                            trackColor = tierColor.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current: $karmaScore",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Next Rank: $nextTierThreshold",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. Lifetime Productivity 4-Metric Grid
            // -------------------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Completed",
                        value = completedCount.toString(),
                        subtitle = "Lifetime tasks",
                        icon = Icons.Filled.CheckCircle,
                        iconTint = Color(0xFF10B981)
                    )
                    MetricStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Active Flow",
                        value = pendingCount.toString(),
                        subtitle = "Pending tasks",
                        icon = Icons.Outlined.FormatListBulleted,
                        iconTint = Color(0xFF3B82F6)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val rate = if (totalTasks > 0) ((completedCount.toFloat() / totalTasks) * 100).toInt() else 100
                    MetricStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Velocity Rate",
                        value = "$rate%",
                        subtitle = "Completion ratio",
                        icon = Icons.Filled.Speed,
                        iconTint = Color(0xFFF59E0B)
                    )
                    MetricStatCard(
                        modifier = Modifier.weight(1f),
                        title = "P1 Cleared",
                        value = p1CompletedCount.toString(),
                        subtitle = "Urgent milestones",
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = Color(0xFFEF4444)
                    )
                }
            }

            // -------------------------------------------------------------
            // 4. Weekly Velocity Heatmap Visualizer (Mon - Sun)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Weekly Activity Pulse",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily completion consistency",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "5d Streak",
                                        color = Color(0xFFEF4444),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 7-day pill columns
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val currentDayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
                            // Convert Sunday=1..Saturday=7 to Mon=0..Sun=6
                            if (it == Calendar.SUNDAY) 6 else it - 2
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            days.forEachIndexed { index, day ->
                                val isToday = (index == currentDayIndex)
                                val dayFill = when {
                                    isToday -> 0.85f
                                    index < currentDayIndex -> (0.4f + (index * 0.1f)).coerceIn(0.2f, 1f)
                                    else -> 0.15f
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(dayFill)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isToday) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. Trophies & Milestone Badges (6 Badges)
            // -------------------------------------------------------------
            item {
                Text(
                    text = "Milestones & Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unlock elite trophies as you complete high-impact work",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(achievements.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { badge ->
                        AchievementCard(
                            modifier = Modifier.weight(1f),
                            badge = badge
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // -------------------------------------------------------------
            // 6. Pro Workspace Tools & Cloud Status
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Executive Shortcuts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ListItem(
                            headlineContent = { Text("Productivity Analytics", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Detailed charts, scores & project health") },
                            leadingContent = {
                                Icon(Icons.Outlined.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onNavigateToAnalytics)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        ListItem(
                            headlineContent = { Text("Smart Focus Mode (Pomodoro)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Launch 25m interval deep work session") },
                            leadingContent = {
                                Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFFF59E0B))
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onNavigateToFocus)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        ListItem(
                            headlineContent = { Text("Export Workspace Data (JSON)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Share or backup full task database") },
                            leadingContent = {
                                Icon(Icons.Outlined.Share, contentDescription = null, tint = Color(0xFF10B981))
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                try {
                                    val gson = GsonBuilder().setPrettyPrinting().create()
                                    val json = gson.toJson(allTasks)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Export Tasks JSON")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        ListItem(
                            headlineContent = { Text("System & App Settings", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Theme mode, notifications, and reminders") },
                            leadingContent = {
                                Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onSettings)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        var showLogoutConfirm by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("Switch Account / Log Out", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
                            supportingContent = { Text(if (email.isNotBlank()) "Logged in as $email" else "Sign out from this device") },
                            leadingContent = {
                                Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { showLogoutConfirm = true }
                        )

                        if (showLogoutConfirm) {
                            AlertDialog(
                                onDismissRequest = { showLogoutConfirm = false },
                                title = { Text("Log Out / Switch Account") },
                                text = { Text("Are you sure you want to log out? You will be prompted to choose an account next time you log in.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showLogoutConfirm = false
                                            SettingsRepository.logout(context) {
                                                onBack()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Log Out")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // -------------------------------------------------------------
    // Edit Profile Modal Bottom Sheet
    // -------------------------------------------------------------
    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false }
        ) {
            var tempName by remember { mutableStateOf(name) }
            var tempUsername by remember { mutableStateOf(username) }
            var tempTitle by remember { mutableStateOf(title) }
            var tempEmail by remember { mutableStateOf(email) }
            var tempBio by remember { mutableStateOf(bio) }
            var tempGoal by remember { mutableIntStateOf(dailyGoal) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Profile Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showEditSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempUsername,
                    onValueChange = { tempUsername = it },
                    label = { Text("Username Handle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    label = { Text("Professional Title / Role") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempEmail,
                    onValueChange = { tempEmail = it },
                    label = { Text("Contact Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("Bio / Philosophy") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        name = tempName.trim().ifEmpty { name }
                        username = tempUsername.trim().ifEmpty { username }
                        title = tempTitle.trim().ifEmpty { title }
                        email = tempEmail.trim().ifEmpty { email }
                        bio = tempBio.trim().ifEmpty { bio }
                        dailyGoal = tempGoal

                        prefs.edit()
                            .putString("name", name)
                            .putString("username", username)
                            .putString("title", title)
                            .putString("email", email)
                            .putString("bio", bio)
                            .putInt("daily_goal", dailyGoal)
                            .apply()

                        SettingsRepository.updateUserProfile(
                            context = context,
                            email = email,
                            name = name,
                            photoUri = imageUri
                        )

                        showEditSheet = false
                        Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Helper UI: Stat Card
// -------------------------------------------------------------
@Composable
private fun MetricStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// Helper UI: Achievement Badge Card
// -------------------------------------------------------------
@Composable
private fun AchievementCard(
    modifier: Modifier = Modifier,
    badge: ProfileAchievement
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isUnlocked) badge.accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = if (badge.isUnlocked) CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(badge.accentColor.copy(alpha = 0.5f), badge.accentColor))) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (badge.isUnlocked) badge.accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        badge.icon,
                        contentDescription = null,
                        tint = if (badge.isUnlocked) badge.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (badge.isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badge.accentColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "UNLOCKED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = badge.accentColor,
                            fontSize = 9.sp
                        )
                    }
                } else {
                    Text(
                        text = "${badge.current}/${badge.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = badge.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = badge.requirement,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
