package com.example.moderntodo.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.PriorityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    tasks: List<TaskEntity>,
    books: List<TaskBookEntity> = emptyList(),
    onBack: () -> Unit
) {
    val totalTasks = tasks.size
    val completedCount = tasks.count { it.isCompleted }
    val pendingCount = tasks.count { !it.isCompleted }
    val now = System.currentTimeMillis()
    val overdueCount = tasks.count {
        !it.isCompleted && ((it.dueTime ?: it.dueDate ?: Long.MAX_VALUE) < now)
    }

    // Productivity Score (0 - 100)
    val completionRatio = if (totalTasks > 0) completedCount.toFloat() / totalTasks else 0f
    val overduePenalty = if (totalTasks > 0) (overdueCount.toFloat() / totalTasks) * 30f else 0f
    val productivityScore = if (totalTasks > 0) {
        ((completionRatio * 100f) - overduePenalty).coerceIn(0f, 100f).toInt()
    } else 100

    val scoreColor = when {
        productivityScore >= 80 -> Color(0xFF10B981) // Emerald
        productivityScore >= 50 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444) // Red
    }

    val scoreStatus = when {
        productivityScore >= 80 -> "High Velocity 🚀"
        productivityScore >= 50 -> "Steady Progress ⚡"
        else -> "Action Required ⚠️"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productivity Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Productivity Gauge & Health Score Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PRODUCTIVITY SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 14.dp.toPx()
                                // Background circle
                                drawCircle(
                                    color = scoreColor.copy(alpha = 0.15f),
                                    style = Stroke(width = strokeWidth)
                                )
                                // Active score arc
                                drawArc(
                                    color = scoreColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * (productivityScore / 100f),
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$productivityScore",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "out of 100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = scoreColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = scoreStatus,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                        }
                    }
                }
            }

            // 2. 4-Metrics Quick Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "Total Tasks",
                        value = "$totalTasks",
                        icon = Icons.Outlined.Checklist,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Completed",
                        value = "$completedCount",
                        icon = Icons.Outlined.TaskAlt,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "In Progress",
                        value = "$pendingCount",
                        icon = Icons.Outlined.PendingActions,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Overdue",
                        value = "$overdueCount",
                        icon = Icons.Outlined.Warning,
                        color = if (overdueCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Priority Execution Breakdown
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Priority Execution Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        PriorityBarRow(
                            label = "P1 - Critical (Urgent)",
                            tasks = tasks.filter { PriorityLevel.fromTask(it).level == 1 },
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        PriorityBarRow(
                            label = "P2 - High Priority",
                            tasks = tasks.filter { PriorityLevel.fromTask(it).level == 2 },
                            color = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        PriorityBarRow(
                            label = "P3 - Medium Priority",
                            tasks = tasks.filter { PriorityLevel.fromTask(it).level == 3 },
                            color = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        PriorityBarRow(
                            label = "P4 - Normal / Low",
                            tasks = tasks.filter { PriorityLevel.fromTask(it).level == 4 },
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            // 4. Project Workspace Health Matrix
            val projectBooks = books.filter { it.isProject }
            if (projectBooks.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Project Workspaces Health",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            projectBooks.forEach { project ->
                                val pTasks = tasks.filter { it.isProjectTask && (it.projectId == project.id || it.bookId == project.id) }
                                val pDone = pTasks.count { it.isCompleted }
                                val pTotal = pTasks.size
                                val pProgress = if (pTotal > 0) pDone.toFloat() / pTotal else 0f

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = project.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$pDone / $pTotal (${(pProgress * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { pProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // 5. Smart Insights & Recommendations
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Productivity Recommendations",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (overdueCount > 0) {
                            Text(
                                text = "• You have $overdueCount overdue items. Clear them using Focus Mode to boost velocity.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (tasks.count { PriorityLevel.fromTask(it).level == 1 && !it.isCompleted } > 0) {
                            Text(
                                text = "• Complete your P1 Critical tasks first thing in the morning for maximum impact.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (completedCount >= 5) {
                            Text(
                                text = "• High consistency! You have completed $completedCount tasks overall.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriorityBarRow(
    label: String,
    tasks: List<TaskEntity>,
    color: Color
) {
    val total = tasks.size
    val done = tasks.count { it.isCompleted }
    val progress = if (total > 0) done.toFloat() / total else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "$done / $total done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}
