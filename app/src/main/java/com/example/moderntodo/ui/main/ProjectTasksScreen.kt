package com.example.moderntodo.ui.main

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.ProjectNoteEntity
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.PriorityLevel
import com.example.moderntodo.util.ProjectPreferences
import com.example.moderntodo.viewmodel.SortOrder
import com.example.moderntodo.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*
sealed class ProjectFeedItem {
    abstract val key: String
    abstract val isCompleted: Boolean
    abstract val priorityLevel: PriorityLevel
    abstract val sortTimestamp: Long
    abstract val title: String

    data class Task(val task: TaskEntity) : ProjectFeedItem() {
        override val key: String = "task_${task.id ?: 0}"
        override val isCompleted: Boolean = task.isCompleted
        override val priorityLevel: PriorityLevel = PriorityLevel.fromTask(task)
        override val sortTimestamp: Long = (task.dueDate ?: 0L) + (task.dueTime ?: 0L)
        override val title: String = task.title
    }

    data class Note(val note: ProjectNoteEntity, val attachedTasks: List<TaskEntity>) : ProjectFeedItem() {
        override val key: String = "note_${note.id ?: 0}"
        override val isCompleted: Boolean = attachedTasks.isNotEmpty() && attachedTasks.all { it.isCompleted }
        override val priorityLevel: PriorityLevel = run {
            if (attachedTasks.isNotEmpty()) {
                val highestAttached = attachedTasks.minOfOrNull { PriorityLevel.fromTask(it).level } ?: 2
                PriorityLevel.fromLevel(highestAttached)
            } else {
                PriorityLevel.HIGH
            }
        }
        override val sortTimestamp: Long = note.updatedAt
        override val title: String = note.title
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTasksScreen(
    viewModel: TaskViewModel,
    projectTaskBooks: List<TaskBookEntity>,
    onEditTask: (TaskEntity) -> Unit,
    onAddNewTask: (targetProjectId: Int?) -> Unit,
    onAddNote: () -> Unit = {},
    onEditNote: (ProjectNoteEntity) -> Unit = {},
    onOpenTemplates: () -> Unit = {}
) {
    val context = LocalContext.current
    val projectTasks by viewModel.projectTasks.collectAsState()
    val allProjectTasks by viewModel.allProjectTasks.collectAsState()
    val projectNotes by viewModel.projectNotes.collectAsState()
    val allProjectNotes by viewModel.allProjectNotes.collectAsState()
    val selectedProjectId by viewModel.selectedProjectId.collectAsState()
    val searchQuery by viewModel.projectSearchQuery.collectAsState()
    val statusFilter by viewModel.projectStatusFilter.collectAsState()
    val priorityFilter by viewModel.projectPriorityFilter.collectAsState()
    val sortOrder by viewModel.projectSortOrder.collectAsState()

    var projectViewMode by remember { mutableStateOf("All") } // "All", "Tasks", or "Notes"
    var showFilterSheet by remember { mutableStateOf(false) }

    val filteredNotes = remember(projectNotes, allProjectNotes, selectedProjectId, searchQuery) {
        val base = if (selectedProjectId != null) {
            projectNotes.filter { it.projectId == selectedProjectId }
        } else {
            allProjectNotes
        }
        if (searchQuery.isNotBlank()) {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        } else {
            base
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // -------------------------------------------------------------
        // 1. Projects Horizontal Selector Strip
        // -------------------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "All Projects" Chip
                    item {
                        val isAllSelected = selectedProjectId == null
                        val totalProjectTasksCount = allProjectTasks.size
                        FilterChip(
                            selected = isAllSelected,
                            onClick = { viewModel.selectProjectFilter(null) },
                            label = {
                                Text(
                                    text = "All Projects ($totalProjectTasksCount)",
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FolderShared,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Individual Project Chips
                    items(projectTaskBooks) { project ->
                        val projId = project.id ?: 0
                        val isSelected = selectedProjectId == projId
                        val projectTasksCount = allProjectTasks.count { (it.projectId == projId || it.bookId == projId) }
                        val projectColorHex = ProjectPreferences.getColorHex(projId)
                        val projectColor = try {
                            Color(android.graphics.Color.parseColor(projectColorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectProjectFilter(if (isSelected) null else projId)
                            },
                            label = {
                                Text(
                                    text = "${project.name} ($projectTasksCount)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(projectColor)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = projectColor.copy(alpha = 0.2f),
                                selectedLabelColor = projectColor
                            )
                        )
                    }
                }

                // -------------------------------------------------------------
                // 2. Search & Filter Row
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateProjectSearchQuery(it) },
                        placeholder = { Text("Search project tasks...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateProjectSearchQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalIconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        val hasActiveFilters = statusFilter != "All" || priorityFilter != null
                        Box {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                            if (hasActiveFilters) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2.5 Unified View Selector: All vs Tasks vs Notes vs Completed
                // -------------------------------------------------------------
                val totalUnifiedCount = projectTasks.size + filteredNotes.size
                val completedProjectTasksCount = remember(allProjectTasks, selectedProjectId) {
                    val base = if (selectedProjectId != null) {
                        allProjectTasks.filter { it.projectId == selectedProjectId || it.bookId == selectedProjectId }
                    } else {
                        allProjectTasks
                    }
                    base.count { it.isCompleted || it.status == "Completed" || it.status == "Full Complete" }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isAllSelected = projectViewMode == "All" && statusFilter != "Completed"
                        FilterChip(
                            selected = isAllSelected,
                            onClick = {
                                projectViewMode = "All"
                                if (statusFilter == "Completed") viewModel.updateProjectStatusFilter("All")
                            },
                            label = { Text("All ($totalUnifiedCount)") },
                            leadingIcon = { Icon(Icons.Outlined.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    item {
                        val isTasksSelected = projectViewMode == "Tasks" && statusFilter != "Completed"
                        FilterChip(
                            selected = isTasksSelected,
                            onClick = {
                                projectViewMode = "Tasks"
                                if (statusFilter == "Completed") viewModel.updateProjectStatusFilter("All")
                            },
                            label = { Text("Tasks (${projectTasks.size})") },
                            leadingIcon = { Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    item {
                        val isNotesSelected = projectViewMode == "Notes"
                        FilterChip(
                            selected = isNotesSelected,
                            onClick = {
                                projectViewMode = "Notes"
                                if (statusFilter == "Completed") viewModel.updateProjectStatusFilter("All")
                            },
                            label = { Text("Notes (${filteredNotes.size})") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    item {
                        val isCompletedSelected = statusFilter == "Completed"
                        FilterChip(
                            selected = isCompletedSelected,
                            onClick = {
                                if (isCompletedSelected) {
                                    viewModel.updateProjectStatusFilter("All")
                                } else {
                                    viewModel.updateProjectStatusFilter("Completed")
                                    projectViewMode = "Tasks"
                                }
                            },
                            label = { Text("Completed ($completedProjectTasksCount)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isCompletedSelected) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Project Tasks & Notes Unified Content (Ordered by Time & Priority)
        // -------------------------------------------------------------
        val feedItems: List<ProjectFeedItem> = remember(
            projectTasks,
            filteredNotes,
            allProjectTasks,
            projectViewMode,
            sortOrder
        ) {
            val items = mutableListOf<ProjectFeedItem>()

            if (projectViewMode == "All" || projectViewMode == "Tasks") {
                items.addAll(projectTasks.map { ProjectFeedItem.Task(it) })
            }

            if (projectViewMode == "All" || projectViewMode == "Notes") {
                items.addAll(filteredNotes.map { note ->
                    val attachedIds = note.attachedTaskIds.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val attached = allProjectTasks.filter { attachedIds.contains(it.id) }
                    ProjectFeedItem.Note(note, attached)
                })
            }

            when (sortOrder) {
                SortOrder.PRIORITY_ASC -> {
                    // Priority & Time: Active items first, then Priority (P1 -> P4), then Time (earliest deadline / latest activity)
                    items.sortedWith { a, b ->
                        if (a.isCompleted != b.isCompleted) {
                            return@sortedWith if (a.isCompleted) 1 else -1
                        }
                        val pCompare = a.priorityLevel.level.compareTo(b.priorityLevel.level)
                        if (pCompare != 0) {
                            return@sortedWith pCompare
                        }
                        val aDue = (a as? ProjectFeedItem.Task)?.task?.dueDate
                        val bDue = (b as? ProjectFeedItem.Task)?.task?.dueDate
                        when {
                            aDue != null && bDue != null -> {
                                val aTime = aDue + ((a as ProjectFeedItem.Task).task.dueTime ?: 0L)
                                val bTime = bDue + ((b as ProjectFeedItem.Task).task.dueTime ?: 0L)
                                aTime.compareTo(bTime)
                            }
                            aDue != null && bDue == null -> -1
                            aDue == null && bDue != null -> 1
                            else -> b.sortTimestamp.compareTo(a.sortTimestamp)
                        }
                    }
                }
                SortOrder.DEADLINE_ASC -> {
                    // Time / Deadline Primary: Active first, then nearest deadline / recent update, then priority
                    items.sortedWith { a, b ->
                        if (a.isCompleted != b.isCompleted) {
                            return@sortedWith if (a.isCompleted) 1 else -1
                        }
                        val aDue = (a as? ProjectFeedItem.Task)?.task?.dueDate
                        val bDue = (b as? ProjectFeedItem.Task)?.task?.dueDate
                        when {
                            aDue != null && bDue != null -> {
                                val aTime = aDue + ((a as ProjectFeedItem.Task).task.dueTime ?: 0L)
                                val bTime = bDue + ((b as ProjectFeedItem.Task).task.dueTime ?: 0L)
                                val tCompare = aTime.compareTo(bTime)
                                if (tCompare != 0) tCompare else a.priorityLevel.level.compareTo(b.priorityLevel.level)
                            }
                            aDue != null && bDue == null -> -1
                            aDue == null && bDue != null -> 1
                            else -> {
                                val tCompare = b.sortTimestamp.compareTo(a.sortTimestamp)
                                if (tCompare != 0) tCompare else a.priorityLevel.level.compareTo(b.priorityLevel.level)
                            }
                        }
                    }
                }
                SortOrder.DATE_CREATED_DESC -> {
                    items.sortedWith { a, b ->
                        if (a.isCompleted != b.isCompleted) {
                            return@sortedWith if (a.isCompleted) 1 else -1
                        }
                        b.sortTimestamp.compareTo(a.sortTimestamp)
                    }
                }
                SortOrder.TITLE_ASC -> {
                    items.sortedWith { a, b ->
                        if (a.isCompleted != b.isCompleted) {
                            return@sortedWith if (a.isCompleted) 1 else -1
                        }
                        a.title.compareTo(b.title, ignoreCase = true)
                    }
                }
                else -> {
                    items.sortedWith { a, b ->
                        if (a.isCompleted != b.isCompleted) {
                            return@sortedWith if (a.isCompleted) 1 else -1
                        }
                        val pCompare = a.priorityLevel.level.compareTo(b.priorityLevel.level)
                        if (pCompare != 0) pCompare else b.sortTimestamp.compareTo(a.sortTimestamp)
                    }
                }
            }
        }

        if (feedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || statusFilter != "All" || priorityFilter != null) {
                            "No project items match your filters"
                        } else {
                            "No project tasks or notes yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Track your project execution and documentation seamlessly in one prioritized timeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onAddNewTask(selectedProjectId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Task")
                        }
                        OutlinedButton(
                            onClick = { onAddNote() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Note")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)
            ) {
                items(feedItems, key = { it.key }) { item ->
                    when (item) {
                        is ProjectFeedItem.Task -> {
                            val task = item.task
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteTask(task, context)
                                        true
                                    } else false
                                }
                            )

                            val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    if (isDismissing) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFFEF4444))
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                                        }
                                    }
                                },
                                content = {
                                    val targetProj = projectTaskBooks.find { (it.id ?: 0) == (task.projectId ?: task.bookId) }
                                    ProjectTaskRow(
                                        task = task,
                                        projectName = targetProj?.name ?: "Project",
                                        projectColorHex = targetProj?.id?.let { ProjectPreferences.getColorHex(it) } ?: "#2563EB",
                                        onToggle = { viewModel.toggleTaskCompletion(context, task) },
                                        onClick = { onEditTask(task) },
                                        onStatusChange = { newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                                        onCommentChange = { newComment -> viewModel.updateTaskComment(task, newComment) },
                                        onPriorityChange = { newPriority -> viewModel.updateTaskPriority(task, newPriority) },
                                        onDelete = { viewModel.deleteTask(task, context) }
                                    )
                                }
                            )
                        }
                        is ProjectFeedItem.Note -> {
                            val note = item.note
                            val targetProj = projectTaskBooks.find { (it.id ?: 0) == note.projectId }

                            ProjectNoteCard(
                                note = note,
                                projectName = targetProj?.name ?: "Project #${note.projectId}",
                                projectColorHex = targetProj?.id?.let { ProjectPreferences.getColorHex(it) } ?: "#2563EB",
                                attachedTasks = item.attachedTasks,
                                onToggleTask = { task -> viewModel.toggleTaskCompletion(context, task) },
                                onEdit = { onEditNote(note) },
                                onDelete = { viewModel.deleteProjectNote(note) },
                                onAddTaskDirect = { noteItem, title, priority ->
                                    viewModel.addTaskDirectToNote(noteItem, title, priority)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Filter & Sort Bottom Sheet for Project Tasks
    // -------------------------------------------------------------
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Project Feed Filter & Sort",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Sort Order
                Text("Sort By", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortOrder == SortOrder.PRIORITY_ASC,
                        onClick = { viewModel.updateProjectSortOrder(SortOrder.PRIORITY_ASC) },
                        label = { Text("Priority & Time") }
                    )
                    FilterChip(
                        selected = sortOrder == SortOrder.DEADLINE_ASC,
                        onClick = { viewModel.updateProjectSortOrder(SortOrder.DEADLINE_ASC) },
                        label = { Text("Deadline") }
                    )
                    FilterChip(
                        selected = sortOrder == SortOrder.DATE_CREATED_DESC,
                        onClick = { viewModel.updateProjectSortOrder(SortOrder.DATE_CREATED_DESC) },
                        label = { Text("Newest") }
                    )
                    FilterChip(
                        selected = sortOrder == SortOrder.TITLE_ASC,
                        onClick = { viewModel.updateProjectSortOrder(SortOrder.TITLE_ASC) },
                        label = { Text("A-Z") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Filter
                Text("Filter by Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Pending", "Working", "Completed").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { viewModel.updateProjectStatusFilter(st) },
                            label = { Text(st) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Priority Filter
                Text("Filter by Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = priorityFilter == null,
                        onClick = { viewModel.setProjectPriorityFilter(null) },
                        label = { Text("All") }
                    )
                    PriorityLevel.values().forEach { pLevel ->
                        FilterChip(
                            selected = priorityFilter == pLevel.level,
                            onClick = {
                                viewModel.setProjectPriorityFilter(if (priorityFilter == pLevel.level) null else pLevel.level)
                            },
                            label = { Text(pLevel.code) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(pLevel.color)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// Dedicated Project Task Card (Compact, Simple Size)
// -------------------------------------------------------------
@Composable
fun ProjectTaskRow(
    task: TaskEntity,
    projectName: String,
    projectColorHex: String,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onStatusChange: (String) -> Unit = {},
    onCommentChange: (String) -> Unit = {},
    onPriorityChange: (Int) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val pLevel = PriorityLevel.fromTask(task)
    val projectColor = try {
        Color(android.graphics.Color.parseColor(projectColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    var showPriorityMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Priority Color Indicator Strip along the left edge
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
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Complete",
                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Center Column: Title & Sub-metadata
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val hasSubInfo = task.description.isNotBlank() ||
                            task.dueDate != null ||
                            task.dueTime != null ||
                            !task.comments.isNullOrBlank() ||
                            ((task.status ?: "Pending") != "Pending") ||
                            task.assignee.isNotBlank() ||
                            task.attachments.isNotBlank() ||
                            task.repeatMode != null ||
                            task.hasAlarm

                    if (hasSubInfo) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (task.description.isNotBlank()) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            if (task.dueDate != null) {
                                val format = SimpleDateFormat("MMM d", Locale.getDefault())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = format.format(Date(task.dueDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if ((task.status ?: "Pending") != "Pending") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable { showStatusMenu = true }
                                ) {
                                    Text(
                                        text = task.status ?: "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            if (!task.comments.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showCommentDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Comment,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "Note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (task.assignee.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = task.assignee,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (task.attachments.isNotBlank()) {
                                val count = task.attachments.split(";;").filter { it.isNotBlank() }.size
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (task.repeatMode != null) {
                                Icon(
                                    imageVector = Icons.Filled.Repeat,
                                    contentDescription = "Repeat",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (task.hasAlarm) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Alarm",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right Badges: Project Chip & Priority Chip & Delete Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Project Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = projectColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(projectColor)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = projectName,
                                color = projectColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Interactive Priority Flag Chip
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = pLevel.color.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { showPriorityMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = pLevel.color,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = pLevel.code,
                                    color = pLevel.color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPriorityMenu,
                            onDismissRequest = { showPriorityMenu = false }
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${levelOption.code} • ${levelOption.label}",
                                                fontWeight = if (levelOption == pLevel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        onPriorityChange(levelOption.level)
                                        showPriorityMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Compact Delete button
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Task",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }

    // Status menu dialog/dropdown
    if (showStatusMenu) {
        AlertDialog(
            onDismissRequest = { showStatusMenu = false },
            title = { Text("Update Task Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pending", "Working", "Partially Complete", "Full Complete").forEach { st ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if ((task.status ?: "Pending") == st) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusChange(st)
                                    showStatusMenu = false
                                }
                        ) {
                            Text(
                                text = st,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = if ((task.status ?: "Pending") == st) FontWeight.Bold else FontWeight.Normal,
                                color = if ((task.status ?: "Pending") == st) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatusMenu = false }) { Text("Cancel") }
            }
        )
    }

    // Comment editor dialog
    if (showCommentDialog) {
        var newComment by remember { mutableStateOf(task.comments ?: "") }
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Task Comments") },
            text = {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCommentChange(newComment.trim())
                    showCommentDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete \"${task.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Project Note Card with Attached Tasks Live Checklist
// -------------------------------------------------------------
@Composable
fun ProjectNoteCard(
    note: ProjectNoteEntity,
    projectName: String,
    projectColorHex: String,
    attachedTasks: List<TaskEntity>,
    onToggleTask: (TaskEntity) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTaskDirect: (ProjectNoteEntity, String, Int) -> Unit = { _, _, _ -> }
) {
    val projectColor = try {
        Color(android.graphics.Color.parseColor(projectColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val highestAttachedPriority = attachedTasks.minOfOrNull { PriorityLevel.fromTask(it).level }
    val pLevel = highestAttachedPriority?.let { PriorityLevel.fromLevel(it) } ?: PriorityLevel.HIGH

    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Note & Priority Left Accent Strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (attachedTasks.isNotEmpty()) pLevel.color else projectColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Header Row: Badges & Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Project Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = projectColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(projectColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = projectName,
                                    color = projectColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Note Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Note",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Priority Badge
                        if (attachedTasks.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = pLevel.color.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Flag,
                                        contentDescription = null,
                                        tint = pLevel.color,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = pLevel.code,
                                        color = pLevel.color,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Note", modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Note", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

            // Note Title
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Formatted Date
            val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
            Text(
                text = "Updated $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Note Content
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Project Note Tasks Section (Interactive Checklist & Direct Add!)
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val completedCount = attachedTasks.count { it.isCompleted }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tasks for this Note",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (attachedTasks.isNotEmpty()) {
                            Text(
                                text = "$completedCount / ${attachedTasks.size} done",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (completedCount == attachedTasks.size) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (attachedTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        attachedTasks.forEach { task ->
                            val pLevel = PriorityLevel.fromTask(task)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { onToggleTask(task) },
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = pLevel.color.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = pLevel.code,
                                        color = pLevel.color,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inline Task Creation directly into this Note
                    var newDirectTaskTitle by remember { mutableStateOf("") }
                    var newDirectPriority by remember { mutableStateOf(PriorityLevel.HIGH) }
                    var showDirectPriorityDropdown by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDirectTaskTitle,
                            onValueChange = { newDirectTaskTitle = it },
                            placeholder = { Text("Add task direct to note...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = newDirectPriority.color.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable { showDirectPriorityDropdown = true }
                                            .padding(end = 4.dp)
                                    ) {
                                        Text(
                                            text = newDirectPriority.code,
                                            color = newDirectPriority.color,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showDirectPriorityDropdown,
                                        onDismissRequest = { showDirectPriorityDropdown = false }
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
                                                    newDirectPriority = levelOption
                                                    showDirectPriorityDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (newDirectTaskTitle.isNotBlank()) {
                                    onAddTaskDirect(note, newDirectTaskTitle.trim(), newDirectPriority.level)
                                    newDirectTaskTitle = ""
                                }
                            },
                            enabled = newDirectTaskTitle.isNotBlank(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (newDirectTaskTitle.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add Task to Note",
                                tint = if (newDirectTaskTitle.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Project Note?") },
            text = { Text("Are you sure you want to delete note '${note.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
