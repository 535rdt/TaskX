package com.example.moderntodo.ui.main

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.ProjectPreferences
import com.example.moderntodo.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsTabContent(
    taskBooks: List<TaskBookEntity>,
    currentBookId: Int,
    allTasks: List<TaskEntity>,
    viewModel: TaskViewModel,
    onSelectProject: (Int) -> Unit,
    onNavigateToAdmin: (Int) -> Unit,
    onScanQrCode: () -> Unit
) {
    val context = LocalContext.current
    val currentUserEmail by com.example.moderntodo.util.SettingsRepository.userEmailFlow.collectAsState()

    // UI state
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Active, Completed, Priority
    var sortBy by remember { mutableStateOf("Recently Created") } // Recently Created, Name, Progress, Tasks
    var isCompactView by remember { mutableStateOf(false) }

    var showCreateSheet by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<TaskBookEntity?>(null) }
    var projectToDelete by remember { mutableStateOf<TaskBookEntity?>(null) }
    var quickAddTaskBookId by remember { mutableStateOf<Int?>(null) }

    val actualProjects = remember(taskBooks) {
        taskBooks.filter { it.isProject || (it.id != null && it.id != 1 && ProjectPreferences.isProject(it.id)) }
    }
    val projectTasksList = remember(allTasks) {
        allTasks.filter { it.isProjectTask }
    }

    // Overall metrics calculation
    val totalProjects = actualProjects.size
    val totalTasksAcrossProjects = projectTasksList.size
    val completedTasksAcrossProjects = projectTasksList.count { it.isCompleted }
    val priorityTasksAcrossProjects = projectTasksList.count { it.isImportant && !it.isCompleted }
    val overallProgress = if (totalTasksAcrossProjects > 0) {
        completedTasksAcrossProjects.toFloat() / totalTasksAcrossProjects
    } else 0f

    // Filter & Sort Projects
    val filteredProjects = remember(actualProjects, allTasks, searchQuery, selectedFilter, sortBy) {
        var list = actualProjects.filter { book ->
            val bookTasks = allTasks.filter { (it.projectId == book.id || it.bookId == book.id) && it.isProjectTask }
            val completed = bookTasks.count { it.isCompleted }
            val total = bookTasks.size
            val is100Done = total > 0 && completed == total
            val hasPriority = bookTasks.any { it.isImportant && !it.isCompleted }

            val matchesSearch = searchQuery.isBlank() ||
                    book.name.contains(searchQuery, ignoreCase = true) ||
                    ProjectPreferences.getDescription(book.id ?: 0).contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Active" -> total == 0 || !is100Done
                "Completed" -> is100Done
                "Priority" -> hasPriority
                else -> true
            }

            matchesSearch && matchesFilter
        }

        when (sortBy) {
            "Name" -> list.sortedBy { it.name.lowercase() }
            "Progress" -> list.sortedByDescending { book ->
                val bTasks = allTasks.filter { (it.projectId == book.id || it.bookId == book.id) && it.isProjectTask }
                if (bTasks.isEmpty()) 0f else bTasks.count { it.isCompleted }.toFloat() / bTasks.size
            }
            "Tasks" -> list.sortedByDescending { book ->
                allTasks.count { (it.projectId == book.id || it.bookId == book.id) && it.isProjectTask }
            }
            else -> list.sortedByDescending { it.id ?: 0 }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // -------------------------------------------------------------
        // 1. Dashboard Executive Summary & Actions
        // -------------------------------------------------------------
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Workspaces & Projects",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage goals, milestones & team boards",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalIconButton(onClick = onScanQrCode) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR Code")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Project", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onScanQrCode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join with QR")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High-Level Metrics Carousel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Overall Progress",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(overallProgress * 100).toInt()}% • $completedTasksAcrossProjects of $totalTasksAcrossProjects Done",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProjectStatBadge(
                                label = "Projects",
                                value = totalProjects.toString(),
                                icon = Icons.Outlined.Folder
                            )
                            ProjectStatBadge(
                                label = "Active Tasks",
                                value = (totalTasksAcrossProjects - completedTasksAcrossProjects).toString(),
                                icon = Icons.Outlined.Schedule
                            )
                            ProjectStatBadge(
                                label = "Priority",
                                value = priorityTasksAcrossProjects.toString(),
                                icon = Icons.Outlined.Flag,
                                tint = Color(0xFFE91E63)
                            )
                            ProjectStatBadge(
                                label = "Completed",
                                value = completedTasksAcrossProjects.toString(),
                                icon = Icons.Outlined.CheckCircle,
                                tint = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Search, Filter Chips & View Mode
        // -------------------------------------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search projects or tags...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter & Sort Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(listOf("All", "Active", "Completed", "Priority")) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }

                    // Compact / Detailed View Toggle
                    IconButton(onClick = { isCompactView = !isCompactView }) {
                        Icon(
                            imageVector = if (isCompactView) Icons.Outlined.GridView else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Toggle View"
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Projects Listing
        // -------------------------------------------------------------
        if (filteredProjects.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No projects match '$searchQuery'" else "No projects yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ New Project' to create your first organized workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredProjects, key = { it.id ?: 0 }) { book ->
                val bookId = book.id ?: 1
                val bookTasks = allTasks.filter { it.bookId == bookId }
                val completedCount = bookTasks.count { it.isCompleted }
                val totalCount = bookTasks.size
                val pendingList = bookTasks.filter { !it.isCompleted }
                val priorityCount = pendingList.count { it.isImportant }

                val hexColor = ProjectPreferences.getColorHex(bookId)
                val iconName = ProjectPreferences.getIconName(bookId)
                val description = ProjectPreferences.getDescription(bookId)
                val deadline = ProjectPreferences.getDeadline(bookId)

                val projectColor = parseProjectColor(hexColor, MaterialTheme.colorScheme.primary)
                val projectIcon = getIconForName(iconName)

                if (isCompactView) {
                    CompactProjectRow(
                        book = book,
                        color = projectColor,
                        icon = projectIcon,
                        totalCount = totalCount,
                        completedCount = completedCount,
                        isSelected = currentBookId == bookId,
                        onOpen = { onSelectProject(bookId) },
                        onQuickAddTask = { quickAddTaskBookId = bookId },
                        onManage = { onNavigateToAdmin(bookId) },
                        onEdit = { projectToEdit = book },
                        onDuplicate = { viewModel.duplicateBook(book) },
                        onCompleteAll = { viewModel.completeAllTasksInBook(bookId) },
                        onDelete = { projectToDelete = book }
                    )
                } else {
                    AdvancedProjectCard(
                        book = book,
                        color = projectColor,
                        icon = projectIcon,
                        description = description,
                        deadline = deadline,
                        totalCount = totalCount,
                        completedCount = completedCount,
                        priorityCount = priorityCount,
                        pendingTasks = pendingList.take(3),
                        isSelected = currentBookId == bookId,
                        onOpen = { onSelectProject(bookId) },
                        onToggleTask = { task -> viewModel.toggleTaskCompletion(context, task) },
                        onQuickAddTask = { quickAddTaskBookId = bookId },
                        onManage = { onNavigateToAdmin(bookId) },
                        onEdit = { projectToEdit = book },
                        onDuplicate = { viewModel.duplicateBook(book) },
                        onCompleteAll = { viewModel.completeAllTasksInBook(bookId) },
                        onDelete = { projectToDelete = book }
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Create Project Bottom Sheet
    // -------------------------------------------------------------
    if (showCreateSheet) {
        CreateProjectSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc, colorHex, iconName, deadline, initialTask ->
                viewModel.addBookWithDetails(
                    name = name,
                    description = desc,
                    colorHex = colorHex,
                    iconName = iconName,
                    deadline = deadline,
                    initialTaskTitle = initialTask,
                    context = context,
                    userEmail = currentUserEmail,
                    onCreated = { newId ->
                        Toast.makeText(context, "Project '$name' created & synced!", Toast.LENGTH_SHORT).show()
                    }
                )
                showCreateSheet = false
            }
        )
    }

    // -------------------------------------------------------------
    // Edit / Customize Project Dialog
    // -------------------------------------------------------------
    projectToEdit?.let { book ->
        CustomizeProjectDialog(
            book = book,
            onDismiss = { projectToEdit = null },
            onSave = { updatedName, updatedDesc, updatedColorHex, updatedIconName, updatedDeadline ->
                viewModel.updateBook(book, updatedName)
                ProjectPreferences.setProjectMetadata(
                    bookId = book.id ?: 1,
                    colorHex = updatedColorHex,
                    iconName = updatedIconName,
                    description = updatedDesc,
                    deadline = updatedDeadline
                )
                Toast.makeText(context, "Project updated", Toast.LENGTH_SHORT).show()
                projectToEdit = null
            }
        )
    }

    // -------------------------------------------------------------
    // Quick Add Task to Project Dialog
    // -------------------------------------------------------------
    quickAddTaskBookId?.let { targetBookId ->
        val targetBook = taskBooks.find { it.id == targetBookId }
        QuickAddTaskDialog(
            projectName = targetBook?.name ?: "Project",
            onDismiss = { quickAddTaskBookId = null },
            onAddTask = { title, priority, dueDate ->
                viewModel.addTaskToBook(
                    bookId = targetBookId,
                    title = title,
                    isImportant = priority <= 2,
                    priority = priority,
                    dueDate = dueDate
                )
                Toast.makeText(context, "Task added to ${targetBook?.name}", Toast.LENGTH_SHORT).show()
                quickAddTaskBookId = null
            }
        )
    }

    // -------------------------------------------------------------
    // Delete Confirmation Dialog
    // -------------------------------------------------------------
    projectToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project '${book.name}'?") },
            text = {
                Text("This will permanently delete this project and all its associated tasks. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (book.id == 1) {
                            Toast.makeText(context, "Default list cannot be deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.deleteBook(book)
                            Toast.makeText(context, "Project deleted", Toast.LENGTH_SHORT).show()
                        }
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Component: Advanced Project Card (Expanded View)
// -------------------------------------------------------------
@Composable
fun AdvancedProjectCard(
    book: TaskBookEntity,
    color: Color,
    icon: ImageVector,
    description: String,
    deadline: Long?,
    totalCount: Int,
    completedCount: Int,
    priorityCount: Int,
    pendingTasks: List<TaskEntity>,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onQuickAddTask: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onCompleteAll: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val isAllCompleted = totalCount > 0 && completedCount == totalCount

    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column {
            // Top Accent Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(color)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row: Icon + Title + Status Pill + Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = book.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (isAllCompleted) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "DONE",
                                        color = Color(0xFF10B981),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "$completedCount/$totalCount tasks completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3-Dots Dropdown Menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage & Export (PDF, QR)") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onManage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Customize Project") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate Project") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Complete All Tasks") },
                                leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onCompleteAll()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete Project", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                // Deadline & Tags Row (if present)
                if (deadline != null || priorityCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (deadline != null) {
                            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            val isOverdue = deadline < System.currentTimeMillis() && !isAllCompleted
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isOverdue) "Overdue: ${sdf.format(Date(deadline))}" else "Due ${sdf.format(Date(deadline))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (priorityCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE91E63).copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFE91E63))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$priorityCount Urgent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE91E63),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Progress Bar
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}% Complete",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Text(
                        text = "$completedCount / $totalCount Done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                // Milestones & Next Tasks Preview
                if (pendingTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Next Milestones",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    pendingTasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onToggleTask(task) },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val pLevel = com.example.moderntodo.util.PriorityLevel.fromTask(task)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = pLevel.color.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Flag,
                                        contentDescription = null,
                                        tint = pLevel.color,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = pLevel.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pLevel.color,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Bar: Quick Add Task + Open Project
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onQuickAddTask,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Task", maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Board", maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Compact Project Row
// -------------------------------------------------------------
@Composable
fun CompactProjectRow(
    book: TaskBookEntity,
    color: Color,
    icon: ImageVector,
    totalCount: Int,
    completedCount: Int,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onQuickAddTask: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onCompleteAll: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = color.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$completedCount/$totalCount done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onQuickAddTask) {
                Icon(Icons.Filled.Add, contentDescription = "Quick Add Task", tint = color)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Manage & Export (PDF, QR)") },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = { menuExpanded = false; onManage() }
                    )
                    DropdownMenuItem(
                        text = { Text("Customize") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menuExpanded = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Complete All") },
                        leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                        onClick = { menuExpanded = false; onCompleteAll() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Create Project Modal Bottom Sheet
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, colorHex: String, iconName: String, deadline: Long?, initialTask: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(ProjectPreferences.COLOR_PALETTE[0]) }
    var selectedIcon by remember { mutableStateOf(ProjectPreferences.ICONS[0]) }
    var selectedDeadline by remember { mutableStateOf<Long?>(null) }
    var initialTask by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val suggestions = listOf("Mobile App v2", "Client Proposal", "Fitness Goals", "Home Renovation", "Exam Prep", "Travel Plans")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create New Project",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Project Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project Name *") },
                placeholder = { Text("e.g. Website Redesign") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Suggestions Chips
            if (name.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { s ->
                        SuggestionChip(
                            onClick = { name = s },
                            label = { Text(s, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description / Objective (Optional)") },
                placeholder = { Text("Key targets and milestones...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Color Theme Picker
            Text(
                text = "Project Color Theme",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ProjectPreferences.COLOR_PALETTE) { hex ->
                    val color = parseProjectColor(hex, Color.Blue)
                    val isSelected = selectedColor == hex
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex }
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Icon Picker
            Text(
                text = "Project Icon",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ProjectPreferences.ICONS) { iconName ->
                    val isSelected = selectedIcon == iconName
                    val icon = getIconForName(iconName)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = iconName },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deadline Picker Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Target Deadline",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (selectedDeadline != null) {
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDeadline!!))
                        } else "No deadline set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                                selectedDeadline = selectedCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (selectedDeadline != null) "Change" else "Pick Date")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Initial Starter Task (Optional)
            OutlinedTextField(
                value = initialTask,
                onValueChange = { initialTask = it },
                label = { Text("Initial First Task (Optional)") },
                placeholder = { Text("e.g. Schedule kickoff meeting") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, desc, selectedColor, selectedIcon, selectedDeadline, initialTask)
                    } else {
                        Toast.makeText(context, "Please enter a project name", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Create Project", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// Component: Customize Project Dialog
// -------------------------------------------------------------
@Composable
fun CustomizeProjectDialog(
    book: TaskBookEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, colorHex: String, iconName: String, deadline: Long?) -> Unit
) {
    val context = LocalContext.current
    val bookId = book.id ?: 1

    var name by remember { mutableStateOf(book.name) }
    var desc by remember { mutableStateOf(ProjectPreferences.getDescription(bookId)) }
    var selectedColor by remember { mutableStateOf(ProjectPreferences.getColorHex(bookId)) }
    var selectedIcon by remember { mutableStateOf(ProjectPreferences.getIconName(bookId)) }
    var selectedDeadline by remember { mutableStateOf(ProjectPreferences.getDeadline(bookId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProjectPreferences.COLOR_PALETTE) { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseProjectColor(hex, Color.Blue))
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProjectPreferences.ICONS) { iconName ->
                        val isSelected = selectedIcon == iconName
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(getIconForName(iconName), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedDeadline != null) {
                            "Due: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDeadline!!))
                        } else "No Deadline",
                        style = MaterialTheme.typography.bodySmall
                    )

                    TextButton(onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                                selectedDeadline = selectedCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text(if (selectedDeadline != null) "Change" else "Set Due Date")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(name, desc, selectedColor, selectedIcon, selectedDeadline)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// Component: Quick Add Task Dialog
// -------------------------------------------------------------
@Composable
fun QuickAddTaskDialog(
    projectName: String,
    onDismiss: () -> Unit,
    onAddTask: (title: String, priority: Int, dueDate: Long?) -> Unit
) {
    val context = LocalContext.current
    var taskTitle by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(4) }
    var dueDate by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Task to $projectName", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Task Title *") },
                    placeholder = { Text("What needs to be done?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Priority Level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.moderntodo.util.PriorityLevel.values().forEach { pLevel ->
                        val isSelected = selectedPriority == pLevel.level
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPriority = pLevel.level },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) pLevel.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, pLevel.color) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = pLevel.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) pLevel.color else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pLevel.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isSelected) pLevel.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (dueDate != null) "Due: " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(dueDate!!))
                        else "No due date set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                                dueDate = selectedCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (dueDate != null) "Change" else "Pick Date")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (taskTitle.isNotBlank()) {
                    onAddTask(taskTitle, selectedPriority, dueDate)
                }
            }) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// Component: Metric Stat Box
// -------------------------------------------------------------
@Composable
fun ProjectStatBadge(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -------------------------------------------------------------
// Helpers
// -------------------------------------------------------------
fun getIconForName(name: String): ImageVector {
    return when (name.lowercase()) {
        "work" -> Icons.Filled.Work
        "code" -> Icons.Filled.Code
        "rocket" -> Icons.Filled.RocketLaunch
        "study" -> Icons.Filled.School
        "star" -> Icons.Filled.Star
        "fitness" -> Icons.Filled.FitnessCenter
        "shopping" -> Icons.Filled.ShoppingCart
        "home" -> Icons.Filled.Home
        else -> Icons.Filled.Folder
    }
}

fun parseProjectColor(hex: String, defaultColor: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        defaultColor
    }
}
