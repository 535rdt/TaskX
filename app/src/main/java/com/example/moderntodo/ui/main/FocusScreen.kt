package com.example.moderntodo.ui.main

import android.media.RingtoneManager
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.PriorityLevel
import com.example.moderntodo.viewmodel.TaskViewModel
import kotlinx.coroutines.delay

enum class PomodoroMode(val label: String, val defaultMinutes: Int) {
    FOCUS("Focus Session", 25),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: TaskViewModel,
    initialTask: TaskEntity? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allTasks by viewModel.allTasks.collectAsState()
    val projectTasks by viewModel.projectTasks.collectAsState()
    val candidateTasks: List<TaskEntity> = remember(allTasks, projectTasks) {
        (allTasks + projectTasks).filter { !it.isCompleted }.distinctBy { it.id }
    }

    var selectedTask by remember { mutableStateOf(initialTask ?: candidateTasks.firstOrNull()) }
    var currentMode by remember { mutableStateOf(PomodoroMode.FOCUS) }
    var totalSeconds by remember { mutableStateOf(currentMode.defaultMinutes * 60) }
    var secondsLeft by remember { mutableStateOf(totalSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    var completedSessionsToday by remember { mutableStateOf(2) }
    var showTaskPicker by remember { mutableStateOf(false) }

    // Countdown Timer Loop
    LaunchedEffect(isRunning, secondsLeft) {
        if (isRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        } else if (isRunning && secondsLeft == 0) {
            isRunning = false
            // Play notification ringtone on timer finish
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notificationUri)
                r?.play()
            } catch (_: Exception) {}

            if (currentMode == PomodoroMode.FOCUS) {
                completedSessionsToday += 1
                currentMode = PomodoroMode.SHORT_BREAK
            } else {
                currentMode = PomodoroMode.FOCUS
            }
            totalSeconds = currentMode.defaultMinutes * 60
            secondsLeft = totalSeconds
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Focus Mode", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Task Picker Button
                    IconButton(onClick = { showTaskPicker = true }) {
                        Icon(Icons.Outlined.PlaylistAddCheck, contentDescription = "Select Task")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector Segmented Tabs
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    PomodoroMode.values().forEach { mode ->
                        val isSelected = currentMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentMode = mode
                                    totalSeconds = mode.defaultMinutes * 60
                                    secondsLeft = totalSeconds
                                    isRunning = false
                                }
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Large Circular Progress Ring with Digital Countdown
            val progress = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f
            val ringColor = when (currentMode) {
                PomodoroMode.FOCUS -> MaterialTheme.colorScheme.primary
                PomodoroMode.SHORT_BREAK -> Color(0xFF10B981) // Emerald Green
                PomodoroMode.LONG_BREAK -> Color(0xFF3B82F6)  // Sky Blue
            }

            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    // Background track
                    drawCircle(
                        color = ringColor.copy(alpha = 0.15f),
                        style = Stroke(width = strokeWidth)
                    )
                    // Active sweep
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mins = secondsLeft / 60
                    val secs = secondsLeft % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "Deep Focus Active" else "Ready to Focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) ringColor else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timer Interactive Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Reset Button
                FilledTonalIconButton(
                    onClick = {
                        isRunning = false
                        secondsLeft = totalSeconds
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset Timer")
                }

                // Play / Pause Primary Button
                Button(
                    onClick = { isRunning = !isRunning },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ringColor)
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Pause" else "Start Focus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // +5 Minutes Extension Button
                FilledTonalIconButton(
                    onClick = {
                        secondsLeft += 5 * 60
                        totalSeconds += 5 * 60
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("+5m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Active Task Card
            if (selectedTask != null) {
                val task = selectedTask!!
                val pColor = PriorityLevel.fromTask(task).color

                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CURRENT OBJECTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { showTaskPicker = true }) {
                                Text("Switch Task")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Complete Task Button directly in Focus Mode
                        Button(
                            onClick = {
                                viewModel.toggleTaskCompletion(context, task)
                                selectedTask = candidateTasks.firstOrNull { it.id != task.id }
                                android.widget.Toast.makeText(context, "🎉 Objective completed!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mark Objective Completed", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTaskPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AddTask, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No Task Selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Tap to pick a task for this session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Daily Focus Streak Footer
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$completedSessionsToday Sessions Today",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "${completedSessionsToday * 25} mins focused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Task Picker Bottom Sheet Dialog
        if (showTaskPicker) {
            AlertDialog(
                onDismissRequest = { showTaskPicker = false },
                title = { Text("Select Objective to Focus On", fontWeight = FontWeight.Bold) },
                text = {
                    if (candidateTasks.isEmpty()) {
                        Text("No active tasks found. Create a task first!")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(candidateTasks) { task ->
                                val pColor = PriorityLevel.fromTask(task).color
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selectedTask?.id == task.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTask = task
                                            showTaskPicker = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(pColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (task.isProjectTask) {
                                                Text(
                                                    text = "📁 Project Task",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTaskPicker = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
