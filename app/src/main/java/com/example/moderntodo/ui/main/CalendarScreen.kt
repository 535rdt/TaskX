package com.example.moderntodo.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.PriorityLevel
import com.example.moderntodo.util.ProjectPreferences
import java.text.SimpleDateFormat
import java.util.*

enum class CalendarViewMode {
    MONTH,
    WEEK,
    AGENDA
}

enum class CalendarFilter {
    ALL,
    DUE_TODAY,
    OVERDUE,
    UPCOMING,
    COMPLETED
}

private fun getStartOfDay(timeMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun getStartOfDay(year: Int, month: Int, day: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun isSameDay(time1: Long?, time2: Long?): Boolean {
    if (time1 == null || time2 == null) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    tasks: List<TaskEntity>,
    taskBooks: List<TaskBookEntity> = emptyList(),
    projectTaskBooks: List<TaskBookEntity> = emptyList(),
    onToggleTask: (TaskEntity) -> Unit = {},
    onEditTask: (TaskEntity) -> Unit = {},
    onAddTaskForDate: (Long) -> Unit = {},
    onQuickAddTask: (title: String, dueDate: Long, priority: Int, bookId: Int?) -> Unit = { _, _, _, _ -> }
) {
    val todayCal = remember { Calendar.getInstance() }
    val todayMidnight = remember(todayCal) { getStartOfDay(todayCal.timeInMillis) }

    var currentMonth by remember { mutableStateOf(todayCal.get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(todayCal.get(Calendar.YEAR)) }
    var selectedDateMillis by remember { mutableStateOf(todayMidnight) }

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var activeFilter by remember { mutableStateOf(CalendarFilter.ALL) }
    var selectedBookFilter by remember { mutableStateOf<Int?>(null) }

    // Quick Task Input state
    var quickTaskTitle by remember { mutableStateOf("") }
    var quickPriority by remember { mutableStateOf(PriorityLevel.HIGH) }
    var showPriorityMenu by remember { mutableStateOf(false) }

    val monthCal = remember(currentMonth, currentYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayHeaderFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Filter tasks by optional book/project filter
    val scopedTasks = remember(tasks, selectedBookFilter) {
        if (selectedBookFilter != null) {
            tasks.filter { it.bookId == selectedBookFilter || it.projectId == selectedBookFilter }
        } else {
            tasks
        }
    }

    // Counts for metrics KPI strip
    val todayTasks = remember(scopedTasks, todayMidnight) {
        scopedTasks.filter { task ->
            task.dueDate != null && isSameDay(task.dueDate, todayMidnight) && !task.isCompleted
        }
    }

    val overdueTasks = remember(scopedTasks, todayMidnight) {
        scopedTasks.filter { task ->
            task.dueDate != null && getStartOfDay(task.dueDate) < todayMidnight && !task.isCompleted
        }
    }

    val upcomingTasks = remember(scopedTasks, todayMidnight) {
        val nextWeekMidnight = todayMidnight + 7 * 24 * 60 * 60 * 1000L
        scopedTasks.filter { task ->
            task.dueDate != null && getStartOfDay(task.dueDate) > todayMidnight && getStartOfDay(task.dueDate) <= nextWeekMidnight && !task.isCompleted
        }
    }

    val completedScheduledTasks = remember(scopedTasks) {
        scopedTasks.filter { it.dueDate != null && it.isCompleted }
    }

    // Tasks for the currently selected date
    val tasksOnSelectedDay = remember(scopedTasks, selectedDateMillis) {
        scopedTasks.filter { task ->
            task.dueDate != null && isSameDay(task.dueDate, selectedDateMillis)
        }.sortedWith(compareBy<TaskEntity> { it.isCompleted }
            .thenBy { it.dueTime ?: Long.MAX_VALUE }
            .thenBy { it.priority })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // -------------------------------------------------------------
        // 1. Top Controls Header: Month / Year, Nav Buttons & Today Button
        // -------------------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = monthFormat.format(monthCal.time),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val isViewingCurrentMonth = currentMonth == todayCal.get(Calendar.MONTH) && currentYear == todayCal.get(Calendar.YEAR)
                        Text(
                            text = if (isViewingCurrentMonth) "Current Month" else "Browsing Calendar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick Jump to Today Button
                        FilledTonalButton(
                            onClick = {
                                val now = Calendar.getInstance()
                                currentYear = now.get(Calendar.YEAR)
                                currentMonth = now.get(Calendar.MONTH)
                                selectedDateMillis = todayMidnight
                                activeFilter = CalendarFilter.ALL
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Today", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }

                        // Prev Month Button
                        IconButton(
                            onClick = {
                                if (currentMonth == 0) {
                                    currentMonth = 11
                                    currentYear -= 1
                                } else {
                                    currentMonth -= 1
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", modifier = Modifier.size(18.dp))
                        }

                        // Next Month Button
                        IconButton(
                            onClick = {
                                if (currentMonth == 11) {
                                    currentMonth = 0
                                    currentYear += 1
                                } else {
                                    currentMonth += 1
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // View Mode Selector Pills (Month vs Week vs Agenda)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = viewMode == CalendarViewMode.MONTH,
                        onClick = { viewMode = CalendarViewMode.MONTH },
                        label = { Text("Month", fontWeight = if (viewMode == CalendarViewMode.MONTH) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = viewMode == CalendarViewMode.WEEK,
                        onClick = { viewMode = CalendarViewMode.WEEK },
                        label = { Text("Week", fontWeight = if (viewMode == CalendarViewMode.WEEK) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { Icon(Icons.Outlined.ViewWeek, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = viewMode == CalendarViewMode.AGENDA,
                        onClick = { viewMode = CalendarViewMode.AGENDA },
                        label = { Text("Agenda", fontWeight = if (viewMode == CalendarViewMode.AGENDA) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { Icon(Icons.Outlined.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // KPI Quick Metrics Strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = activeFilter == CalendarFilter.ALL,
                            onClick = { activeFilter = CalendarFilter.ALL },
                            label = { Text("All Scheduled (${scopedTasks.count { it.dueDate != null }})") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = activeFilter == CalendarFilter.DUE_TODAY,
                            onClick = {
                                activeFilter = CalendarFilter.DUE_TODAY
                                selectedDateMillis = todayMidnight
                                currentMonth = todayCal.get(Calendar.MONTH)
                                currentYear = todayCal.get(Calendar.YEAR)
                            },
                            label = { Text("Today (${todayTasks.size})") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        val hasOverdue = overdueTasks.isNotEmpty()
                        FilterChip(
                            selected = activeFilter == CalendarFilter.OVERDUE,
                            onClick = {
                                activeFilter = CalendarFilter.OVERDUE
                                viewMode = CalendarViewMode.AGENDA
                            },
                            label = { Text("Overdue (${overdueTasks.size})") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (hasOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = activeFilter == CalendarFilter.UPCOMING,
                            onClick = { activeFilter = CalendarFilter.UPCOMING },
                            label = { Text("Upcoming (${upcomingTasks.size})") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = activeFilter == CalendarFilter.COMPLETED,
                            onClick = { activeFilter = CalendarFilter.COMPLETED },
                            label = { Text("Completed (${completedScheduledTasks.size})") },
                            leadingIcon = {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Main Calendar Content (Month vs Week vs Agenda)
        // -------------------------------------------------------------
        when (viewMode) {
            CalendarViewMode.MONTH -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        // Monthly Interactive Calendar Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Day of week headers: Sun Mon Tue Wed Thu Fri Sat
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { idx, dayName ->
                                        Text(
                                            text = dayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (idx == 0 || idx == 6) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Days Grid
                                val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sun, 1 for Mon

                                val totalCells = firstDayOfWeek + daysInMonth
                                val rowCount = (totalCells + 6) / 7

                                for (rowIndex in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (colIndex in 0 until 7) {
                                            val cellIndex = rowIndex * 7 + colIndex
                                            val dayNumber = cellIndex - firstDayOfWeek + 1

                                            if (dayNumber in 1..daysInMonth) {
                                                val cellDateMillis = getStartOfDay(currentYear, currentMonth, dayNumber)
                                                val isSelected = isSameDay(cellDateMillis, selectedDateMillis)
                                                val isToday = isSameDay(cellDateMillis, todayMidnight)

                                                val dayTasks = scopedTasks.filter { task ->
                                                    task.dueDate != null && isSameDay(task.dueDate, cellDateMillis)
                                                }
                                                val hasActiveTasks = dayTasks.any { !it.isCompleted }
                                                val isAllComplete = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .padding(2.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            when {
                                                                isSelected -> MaterialTheme.colorScheme.primary
                                                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .then(
                                                            if (isToday && !isSelected) {
                                                                Modifier.background(
                                                                    color = Color.Transparent,
                                                                    shape = RoundedCornerShape(10.dp)
                                                                )
                                                            } else Modifier
                                                        )
                                                        .clickable {
                                                            selectedDateMillis = cellDateMillis
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = dayNumber.toString(),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                            color = when {
                                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                                isToday -> MaterialTheme.colorScheme.primary
                                                                else -> MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )

                                                        // Colored Task Dots
                                                        if (dayTasks.isNotEmpty()) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                if (isAllComplete) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(5.dp)
                                                                            .clip(CircleShape)
                                                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF10B981))
                                                                    )
                                                                } else {
                                                                    // Up to 3 priority dots
                                                                    val sortedDots = dayTasks
                                                                        .filter { !it.isCompleted }
                                                                        .sortedBy { it.priority }
                                                                        .take(3)

                                                                    sortedDots.forEach { t ->
                                                                        val dotColor = if (isSelected) {
                                                                            MaterialTheme.colorScheme.onPrimary
                                                                        } else {
                                                                            PriorityLevel.fromTask(t).color
                                                                        }
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(4.dp)
                                                                                .clip(CircleShape)
                                                                                .background(dotColor)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Empty spacing cell
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selected Day Agenda Section
                    item {
                        DayScheduleCard(
                            selectedDateMillis = selectedDateMillis,
                            todayMidnight = todayMidnight,
                            tasks = tasksOnSelectedDay,
                            quickTaskTitle = quickTaskTitle,
                            onQuickTaskTitleChange = { quickTaskTitle = it },
                            quickPriority = quickPriority,
                            onQuickPriorityChange = { quickPriority = it },
                            showPriorityMenu = showPriorityMenu,
                            onShowPriorityMenuChange = { showPriorityMenu = it },
                            onQuickAdd = {
                                if (quickTaskTitle.isNotBlank()) {
                                    onQuickAddTask(
                                        quickTaskTitle.trim(),
                                        selectedDateMillis,
                                        quickPriority.level,
                                        selectedBookFilter
                                    )
                                    quickTaskTitle = ""
                                }
                            },
                            onAddNewTask = { onAddTaskForDate(selectedDateMillis) },
                            onToggleTask = onToggleTask,
                            onEditTask = onEditTask,
                            taskBooks = taskBooks,
                            projectTaskBooks = projectTaskBooks
                        )
                    }
                }
            }

            CalendarViewMode.WEEK -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        // 7-Day Horizontal Week Strip
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Week navigation header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectedDateMillis -= 7 * 24 * 60 * 60 * 1000L
                                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                            currentMonth = cal.get(Calendar.MONTH)
                                            currentYear = cal.get(Calendar.YEAR)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "Week of " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMillis)),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    IconButton(
                                        onClick = {
                                            selectedDateMillis += 7 * 24 * 60 * 60 * 1000L
                                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                            currentMonth = cal.get(Calendar.MONTH)
                                            currentYear = cal.get(Calendar.YEAR)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 7-Day Row
                                val weekStartCal = Calendar.getInstance().apply {
                                    timeInMillis = selectedDateMillis
                                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (i in 0..6) {
                                        val dayCal = Calendar.getInstance().apply {
                                            timeInMillis = weekStartCal.timeInMillis
                                            add(Calendar.DAY_OF_MONTH, i)
                                        }
                                        val dayMillis = dayCal.timeInMillis
                                        val isSelected = isSameDay(dayMillis, selectedDateMillis)
                                        val isToday = isSameDay(dayMillis, todayMidnight)

                                        val dayTasks = scopedTasks.filter { task ->
                                            task.dueDate != null && isSameDay(task.dueDate, dayMillis)
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { selectedDateMillis = dayMillis }
                                                .padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCal.time).take(3),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )

                                            Spacer(Modifier.height(4.dp))
                                            if (dayTasks.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                            else if (dayTasks.all { it.isCompleted }) Color(0xFF10B981)
                                                            else MaterialTheme.colorScheme.primary
                                                        )
                                                )
                                            } else {
                                                Spacer(Modifier.height(5.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Day Agenda
                    item {
                        DayScheduleCard(
                            selectedDateMillis = selectedDateMillis,
                            todayMidnight = todayMidnight,
                            tasks = tasksOnSelectedDay,
                            quickTaskTitle = quickTaskTitle,
                            onQuickTaskTitleChange = { quickTaskTitle = it },
                            quickPriority = quickPriority,
                            onQuickPriorityChange = { quickPriority = it },
                            showPriorityMenu = showPriorityMenu,
                            onShowPriorityMenuChange = { showPriorityMenu = it },
                            onQuickAdd = {
                                if (quickTaskTitle.isNotBlank()) {
                                    onQuickAddTask(
                                        quickTaskTitle.trim(),
                                        selectedDateMillis,
                                        quickPriority.level,
                                        selectedBookFilter
                                    )
                                    quickTaskTitle = ""
                                }
                            },
                            onAddNewTask = { onAddTaskForDate(selectedDateMillis) },
                            onToggleTask = onToggleTask,
                            onEditTask = onEditTask,
                            taskBooks = taskBooks,
                            projectTaskBooks = projectTaskBooks
                        )
                    }
                }
            }

            CalendarViewMode.AGENDA -> {
                // Agenda Continuous Feed
                val allScheduledTasks = remember(scopedTasks, activeFilter, todayMidnight) {
                    val base = when (activeFilter) {
                        CalendarFilter.OVERDUE -> scopedTasks.filter { it.dueDate != null && getStartOfDay(it.dueDate) < todayMidnight && !it.isCompleted }
                        CalendarFilter.DUE_TODAY -> scopedTasks.filter { it.dueDate != null && isSameDay(it.dueDate, todayMidnight) }
                        CalendarFilter.UPCOMING -> scopedTasks.filter { it.dueDate != null && getStartOfDay(it.dueDate) > todayMidnight && !it.isCompleted }
                        CalendarFilter.COMPLETED -> scopedTasks.filter { it.dueDate != null && it.isCompleted }
                        CalendarFilter.ALL -> scopedTasks.filter { it.dueDate != null }
                    }
                    base.sortedWith(
                        compareBy<TaskEntity> { it.isCompleted }
                            .thenBy { it.dueDate ?: Long.MAX_VALUE }
                            .thenBy { it.dueTime ?: Long.MAX_VALUE }
                    )
                }

                if (allScheduledTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No Scheduled Tasks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "All clear! Tap below to schedule a new task.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { onAddTaskForDate(todayMidnight) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Schedule Task")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                    ) {
                        items(allScheduledTasks, key = { it.id ?: it.syncId ?: it.title }) { task ->
                            val targetBook = taskBooks.find { it.id == task.bookId } ?: projectTaskBooks.find { it.id == task.bookId }
                            val targetColorHex = targetBook?.id?.let { ProjectPreferences.getColorHex(it) } ?: "#2563EB"

                            CalendarTaskItemRow(
                                task = task,
                                bookName = targetBook?.name ?: "List",
                                bookColorHex = targetColorHex,
                                onToggle = { onToggleTask(task) },
                                onClick = { onEditTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Day Schedule Section Composable
// -------------------------------------------------------------
@Composable
private fun DayScheduleCard(
    selectedDateMillis: Long,
    todayMidnight: Long,
    tasks: List<TaskEntity>,
    quickTaskTitle: String,
    onQuickTaskTitleChange: (String) -> Unit,
    quickPriority: PriorityLevel,
    onQuickPriorityChange: (PriorityLevel) -> Unit,
    showPriorityMenu: Boolean,
    onShowPriorityMenuChange: (Boolean) -> Unit,
    onQuickAdd: () -> Unit,
    onAddNewTask: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    taskBooks: List<TaskBookEntity>,
    projectTaskBooks: List<TaskBookEntity>
) {
    val isToday = isSameDay(selectedDateMillis, todayMidnight)
    val dayHeaderFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val dateLabel = remember(selectedDateMillis) {
        val formatted = dayHeaderFormat.format(Date(selectedDateMillis))
        if (isToday) "$formatted • Today" else formatted
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Date & Add Task Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val completedCount = tasks.count { it.isCompleted }
                    Text(
                        text = "${tasks.size} scheduled • $completedCount completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddNewTask,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Task for Date", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inline 1-Tap Quick Task Input for Selected Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickTaskTitle,
                    onValueChange = onQuickTaskTitleChange,
                    placeholder = {
                        Text(
                            text = "Add task for ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(selectedDateMillis))}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = quickPriority.color.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .clickable { onShowPriorityMenuChange(true) }
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = quickPriority.code,
                                    color = quickPriority.color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showPriorityMenu,
                                onDismissRequest = { onShowPriorityMenuChange(false) }
                            ) {
                                PriorityLevel.values().forEach { levelOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(levelOption.color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${levelOption.code} • ${levelOption.label}")
                                            }
                                        },
                                        onClick = {
                                            onQuickPriorityChange(levelOption)
                                            onShowPriorityMenuChange(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onQuickAdd,
                    enabled = quickTaskTitle.isNotBlank(),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (quickTaskTitle.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Save Task",
                        tint = if (quickTaskTitle.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task List for Day
            if (tasks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No tasks scheduled for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tasks.forEach { task ->
                        val targetBook = taskBooks.find { it.id == task.bookId } ?: projectTaskBooks.find { it.id == task.bookId }
                        val targetColorHex = targetBook?.id?.let { ProjectPreferences.getColorHex(it) } ?: "#2563EB"

                        CalendarTaskItemRow(
                            task = task,
                            bookName = targetBook?.name ?: "Task",
                            bookColorHex = targetColorHex,
                            onToggle = { onToggleTask(task) },
                            onClick = { onEditTask(task) }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sleek Compact Task Item Row for Calendar Feed
// -------------------------------------------------------------
@Composable
private fun CalendarTaskItemRow(
    task: TaskEntity,
    bookName: String,
    bookColorHex: String,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val pLevel = PriorityLevel.fromTask(task)
    val bookColor = try {
        Color(android.graphics.Color.parseColor(bookColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left priority stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(pLevel.color)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Complete toggle checkbox
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Toggle Complete",
                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Sub-row: Time & Book info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Due Time
                        if (task.dueTime != null) {
                            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(task.dueTime))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (task.dueDate != null) {
                            val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(task.dueDate))
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Project / List Tag
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = bookColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(bookColor)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = bookName,
                                    color = bookColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Priority Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pLevel.color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = pLevel.code,
                        color = pLevel.color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
