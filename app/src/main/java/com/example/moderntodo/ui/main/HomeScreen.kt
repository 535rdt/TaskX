package com.example.moderntodo.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.ProjectNoteEntity
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.data.remote.SharedBookService
import com.example.moderntodo.util.QrScannerHelper
import com.example.moderntodo.util.SettingsRepository
import com.example.moderntodo.viewmodel.SortOrder
import com.example.moderntodo.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    onNavigateToCompleted: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: (Int) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFocus: (TaskEntity?) -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val taskBooks by viewModel.taskBooks.collectAsState()
    val normalTaskBooks by viewModel.normalTaskBooks.collectAsState()
    val projectTaskBooks by viewModel.projectTaskBooks.collectAsState()
    val pendingTasks by viewModel.pendingTasks.collectAsState()
    val allPendingTasks by viewModel.allPendingTasks.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()

    var currentBottomTab by remember { mutableStateOf("Tasks") }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var showAddTask by remember { mutableStateOf(false) }
    var isAddingProjectTask by remember { mutableStateOf(false) }
    var targetProjectIdForAdd by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isCreatingList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var showOmniSearch by remember { mutableStateOf(false) }

    // Project Speed-Dial / 3-Button Modal & Notes & Templates
    var showProjectActionMenu by remember { mutableStateOf(false) }
    var showProjectNoteSheet by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<ProjectNoteEntity?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    // Navigation drawer: Profile Menu
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentUserEmail by SettingsRepository.userEmailFlow.collectAsState()
    val isLiveSyncing by viewModel.isLiveSyncing.collectAsState()

    // Silently publish all user lists to cloud so invite codes are immediately active
    LaunchedEffect(currentUserEmail) {
        SharedBookService.syncAllUserBooksToCloud(context, viewModel.repository, currentUserEmail)
    }

    // Catch-up sync whenever current list is viewed
    LaunchedEffect(currentBookId, currentUserEmail) {
        val inviteCode = SharedBookService.getSavedInviteCode(context, currentBookId, currentUserEmail)
        if (!inviteCode.isNullOrBlank()) {
            val userEmail = currentUserEmail ?: SettingsRepository.userEmailFlow.first()
            val userName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "You"
            SharedBookService.syncSharedBookCatchUp(
                context = context,
                repository = viewModel.repository,
                bookId = currentBookId,
                currentUserName = userName,
                currentUserEmail = userEmail,
                force = false
            )
        }
    }

    val currentBook = taskBooks.find { it.id == currentBookId }
    val clipboardManager = LocalClipboardManager.current
    var showJoinSharedListDialog by remember { mutableStateOf(false) }

    val handleProcessSharedInput: (String, (() -> Unit)?) -> Unit = { rawResult, onComplete ->
        scope.launch {
            try {
                val payload = SharedBookService.parseSharedInput(rawResult)
                if (payload != null) {
                    val userEmail = currentUserEmail ?: SettingsRepository.userEmailFlow.first()
                    val userName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "You"
                    val newBookId = SharedBookService.importSharedBook(
                        context = context,
                        repository = viewModel.repository,
                        payload = payload,
                        currentUserName = userName,
                        currentUserEmail = userEmail
                    )
                    viewModel.switchBook(newBookId)
                    Toast.makeText(context, "Joined '${payload.book.name}' successfully! (${payload.tasks.size} tasks)", Toast.LENGTH_LONG).show()
                    showJoinSharedListDialog = false
                    return@launch
                }

                val listId = QrScannerHelper.parseListId(rawResult)
                if (listId != null) {
                    val existing = taskBooks.find { it.id == listId }
                    if (existing != null) {
                        viewModel.switchBook(listId)
                        Toast.makeText(context, "Switched to shared list: ${existing.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addBook("Shared List #$listId", context, currentUserEmail)
                        Toast.makeText(context, "Joined shared list #$listId!", Toast.LENGTH_SHORT).show()
                    }
                    showJoinSharedListDialog = false
                } else {
                    Toast.makeText(context, "Could not find list for: $rawResult. Please check the invite code.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to join: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    val handleScanQr: () -> Unit = {
        QrScannerHelper.startScan(
            context = context,
            onSuccess = { rawResult ->
                handleProcessSharedInput(rawResult, null)
            }
        )
    }

    BackHandler(enabled = showAddTask) {
        showAddTask = false
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                tasks = allTasks,
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onNavigateToProfile = {
                    scope.launch { drawerState.close() }
                    onNavigateToProfile()
                },
                onNavigateToAnalytics = {
                    scope.launch { drawerState.close() }
                    onNavigateToAnalytics()
                },
                onNavigateToFocus = {
                    scope.launch { drawerState.close() }
                    onNavigateToFocus(null)
                },
                onNavigateToCompleted = {
                    scope.launch { drawerState.close() }
                    onNavigateToCompleted()
                },
                isLiveSyncing = isLiveSyncing,
                onTriggerSync = {
                    viewModel.triggerManualSync(context, currentBookId) { result ->
                        Toast.makeText(
                            context,
                            if (result.success) "Cloud sync completed" else "Sync note: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onExportData = {
                    Toast.makeText(context, "Exporting data...", Toast.LENGTH_SHORT).show()
                },
                onImportData = {
                    Toast.makeText(context, "Importing data...", Toast.LENGTH_SHORT).show()
                },
                onLogOut = {
                    SettingsRepository.logout(context)
                }
            )
        }
    ) {
        Scaffold(
                        topBar = {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                title = {
                                    Column {
                                        Text(
                                            text = when (currentBottomTab) {
                                                "Project Tasks" -> "Project Tasks"
                                                "Projects" -> "Projects"
                                                "Calendar" -> "Calendar"
                                                else -> currentBook?.name ?: "Tasks"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        if (currentBottomTab == "Tasks") {
                                            val completedCount = allTasks.count { it.bookId == currentBookId && it.isCompleted }
                                            Text(
                                                text = "${pendingTasks.size} pending • $completedCount completed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    ) {
                                        Icon(
                                            Icons.Filled.Menu,
                                            contentDescription = "Profile Menu",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                actions = {
                                    // Universal Spotlight Search
                                    IconButton(
                                        onClick = { showOmniSearch = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "Spotlight Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Focus / Pomodoro Mode
                                    IconButton(
                                        onClick = { onNavigateToFocus(null) },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Outlined.Timer, contentDescription = "Focus Mode", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Quick Completed Tasks & Filter Buttons (on normal Tasks tab)
                                    if (currentBottomTab == "Tasks") {
                                        IconButton(
                                            onClick = onNavigateToCompleted,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Completed Tasks",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))

                                        IconButton(
                                            onClick = { showFilterSheet = true },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        ) {
                                            Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }

                                    // Profile Button (opens endDrawerState)
                                    val currentUserName by SettingsRepository.userNameFlow.collectAsState()
                                    val avatarInitials = remember(currentUserName, currentUserEmail) {
                                        val name = currentUserName ?: currentUserEmail?.substringBefore("@") ?: "U"
                                        name.take(2).uppercase()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable { scope.launch { drawerState.open() } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = avatarInitials,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp
                            ) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Outlined.Checklist, contentDescription = "Tasks") },
                                    label = { Text("Tasks", fontWeight = if (currentBottomTab == "Tasks") FontWeight.Bold else FontWeight.Normal) },
                                    selected = currentBottomTab == "Tasks",
                                    onClick = { currentBottomTab = "Tasks" },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Outlined.Assignment, contentDescription = "Project Tasks") },
                                    label = { Text("Projects", maxLines = 1, fontWeight = if (currentBottomTab == "Project Tasks") FontWeight.Bold else FontWeight.Normal) },
                                    selected = currentBottomTab == "Project Tasks",
                                    onClick = { currentBottomTab = "Project Tasks" },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Outlined.Folder, contentDescription = "Lists") },
                                    label = { Text("Lists", fontWeight = if (currentBottomTab == "Projects") FontWeight.Bold else FontWeight.Normal) },
                                    selected = currentBottomTab == "Projects",
                                    onClick = { currentBottomTab = "Projects" },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar") },
                                    label = { Text("Calendar", fontWeight = if (currentBottomTab == "Calendar") FontWeight.Bold else FontWeight.Normal) },
                                    selected = currentBottomTab == "Calendar",
                                    onClick = { currentBottomTab = "Calendar" },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        },
                        floatingActionButton = {
                            if (currentBottomTab == "Tasks" || currentBottomTab == "Project Tasks" || currentBottomTab == "Calendar") {
                                FloatingActionButton(
                                    onClick = {
                                        if (currentBottomTab == "Project Tasks") {
                                            showProjectActionMenu = true
                                        } else {
                                            taskToEdit = null
                                            isAddingProjectTask = false
                                            targetProjectIdForAdd = null
                                            showAddTask = true
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add Task", modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentBottomTab) {
                                "Tasks" -> {
                                    val completedCount = remember(allTasks, pendingTasks, currentBookId) {
                                        allTasks.count { it.bookId == currentBookId && it.isCompleted }
                                    }
                                    val totalCount = pendingTasks.size + completedCount
                                    val progressPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 100

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                    ) {
                                        // Quick List Switcher Horizontal Pills
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(normalTaskBooks) { book ->
                                                val isSelected = book.id == currentBookId
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { book.id?.let { viewModel.switchBook(it) } },
                                                    label = { Text(book.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                        containerColor = MaterialTheme.colorScheme.surface,
                                                        labelColor = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                )
                                            }

                                            item {
                                                AssistChip(
                                                    onClick = {
                                                        newListName = ""
                                                        showRenameDialog = true
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                    label = { Text("New List") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                )
                                            }
                                        }

                                        // Productivity Summary Hero Card (like LOSE EXP KPI Card!)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(18.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Today's Progress",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "$completedCount of $totalCount tasks completed",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Badge %
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "$progressPercent%",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.height(12.dp))

                                                // Progress Bar
                                                LinearProgressIndicator(
                                                    progress = { if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 1f },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                )

                                                Spacer(Modifier.height(14.dp))

                                                // Quick KPI Badges
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "${pendingTasks.size} Pending",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { onNavigateToCompleted() }
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "$completedCount Done →",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF10B981)
                                                        )
                                                    }

                                                    Text(
                                                        text = "Analytics →",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.clickable { onNavigateToAnalytics() }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        // Prominent Tasks & Completed Action Row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Tasks (${pendingTasks.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            FilledTonalButton(
                                                onClick = onNavigateToCompleted,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                                                    contentColor = Color(0xFF10B981)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF10B981)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Completed ($completedCount)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (pendingTasks.isEmpty()) {
                                            // Modern Empty State Card
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .weight(1f)
                                                    .padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(72.dp)
                                                            .clip(RoundedCornerShape(22.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Checklist,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(38.dp)
                                                        )
                                                    }

                                                    Spacer(Modifier.height(16.dp))

                                                    Text(
                                                        text = "All Caught Up!",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    Spacer(Modifier.height(6.dp))

                                                    Text(
                                                        text = "No pending tasks in '${currentBook?.name ?: "this list"}'. Tap below to create one or view completed.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )

                                                    Spacer(Modifier.height(20.dp))

                                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        FilledTonalButton(
                                                            onClick = {
                                                                taskToEdit = null
                                                                isAddingProjectTask = false
                                                                targetProjectIdForAdd = null
                                                                showAddTask = true
                                                            },
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("New Task", fontWeight = FontWeight.Bold)
                                                        }

                                                        OutlinedButton(
                                                            onClick = onNavigateToCompleted,
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF10B981))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Completed ($completedCount)", fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                items(pendingTasks, key = { it.id ?: 0 }) { task ->
                                                    val dismissState = rememberSwipeToDismissBoxState(
                                                        confirmValueChange = {
                                                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                                                viewModel.deleteTask(task, context)
                                                                true
                                                            } else false
                                                        }
                                                    )
                                                    SwipeToDismissBox(
                                                        state = dismissState,
                                                        enableDismissFromStartToEnd = false,
                                                        enableDismissFromEndToStart = true,
                                                        backgroundContent = {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                                                                    .clip(RoundedCornerShape(16.dp))
                                                                    .background(Color(0xFFEF4444))
                                                                    .padding(horizontal = 16.dp),
                                                                contentAlignment = Alignment.CenterEnd
                                                            ) {
                                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                                                            }
                                                        },
                                                        content = {
                                                            TaskRow(
                                                                task = task,
                                                                onToggle = { viewModel.toggleTaskCompletion(context, task) },
                                                                onClick = {
                                                                    taskToEdit = task
                                                                    showAddTask = true
                                                                },
                                                                onStatusChange = { newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                                                                onCommentChange = { newComment -> viewModel.updateTaskComment(task, newComment) },
                                                                onPriorityChange = { newPriority -> viewModel.updateTaskPriority(task, newPriority) },
                                                                onStartFocus = { onNavigateToFocus(task) }
                                                            )
                                                        }
                                                    )
                                                }

                                                item {
                                                    TextButton(
                                                        onClick = onNavigateToCompleted,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 16.dp)
                                                    ) {
                                                        Text("View Completed Tasks ($completedCount)")
                                                    }
                                                }
                                                item { Spacer(modifier = Modifier.height(16.dp)) }
                                            }
                                        }
                                    }
                                }
                                "Project Tasks" -> {
                                    ProjectTasksScreen(
                                        viewModel = viewModel,
                                        projectTaskBooks = projectTaskBooks,
                                        onEditTask = { task ->
                                            taskToEdit = task
                                            isAddingProjectTask = true
                                            targetProjectIdForAdd = task.projectId ?: task.bookId
                                            showAddTask = true
                                        },
                                        onAddNewTask = { targetProjId ->
                                            targetProjectIdForAdd = targetProjId ?: viewModel.selectedProjectId.value
                                            showProjectActionMenu = true
                                        },
                                        onAddNote = {
                                            noteToEdit = null
                                            showProjectNoteSheet = true
                                        },
                                        onEditNote = { note ->
                                            noteToEdit = note
                                            showProjectNoteSheet = true
                                        },
                                        onOpenTemplates = {
                                            showTemplateDialog = true
                                        }
                                    )
                                }
                                "Projects" -> {
                                    ProjectsTabContent(
                                        taskBooks = taskBooks,
                                        currentBookId = currentBookId,
                                        allTasks = allTasks,
                                        viewModel = viewModel,
                                        onSelectProject = { bookId ->
                                            viewModel.selectProjectFilter(bookId)
                                            currentBottomTab = "Project Tasks"
                                        },
                                        onNavigateToAdmin = { bookId ->
                                            onNavigateToAdmin(bookId)
                                        },
                                        onScanQrCode = { showJoinSharedListDialog = true }
                                    )
                                }
                                "Calendar" -> {
                                    CalendarScreen(
                                        tasks = allTasks,
                                        taskBooks = taskBooks,
                                        projectTaskBooks = projectTaskBooks,
                                        onToggleTask = { task -> viewModel.toggleTaskCompletion(context, task) },
                                        onEditTask = { task ->
                                            taskToEdit = task
                                            showAddTask = true
                                        },
                                        onAddTaskForDate = { dateMillis ->
                                            taskToEdit = TaskEntity(
                                                title = "",
                                                dueDate = dateMillis,
                                                bookId = currentBookId
                                            )
                                            showAddTask = true
                                        },
                                        onQuickAddTask = { title, dateMillis, priorityLevel, bookId ->
                                            viewModel.addTaskToBook(
                                                bookId = bookId ?: currentBookId,
                                                title = title,
                                                dueDate = dateMillis,
                                                priority = priorityLevel,
                                                isProjectTask = false
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

    // Rename List Dialog
    if (showRenameDialog && currentBook != null) {
        var editedName by remember { mutableStateOf(currentBook.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename List") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("List Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedName.isNotBlank()) {
                        viewModel.updateBook(currentBook, editedName)
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Filter Sheet
    if (showFilterSheet) {
        FilterSheet(viewModel = viewModel, onDismiss = { showFilterSheet = false })
    }

    // Add / Edit Task Modal Sheet
    if (showAddTask) {
        val ctx = context
        val isProj = isAddingProjectTask || (taskToEdit?.isProjectTask == true)
        val projId = targetProjectIdForAdd ?: taskToEdit?.projectId ?: (if (isProj) taskToEdit?.bookId else null)
        AddTaskSheet(
            existingTask = taskToEdit,
            isProjectTaskDefault = isProj,
            projectIdDefault = projId,
            normalTaskBooks = normalTaskBooks,
            projectTaskBooks = projectTaskBooks,
            onDismiss = { showAddTask = false },
            onSave = { title, desc, date, time, alarm, ringtoneUri, repeatMode, rInterval, rUnit, rStarts, rEndsType, rEndsValue, rDaysOfWeek, rMonths, rYears, isFav, isImp, subs, tags, priority, isProjectTask, savedProjectId, status, comments, attachments, assignee ->
                viewModel.saveTask(
                    ctx, title, desc, date, time, alarm, ringtoneUri, repeatMode,
                    rInterval, rUnit, rStarts, rEndsType, rEndsValue, rDaysOfWeek, rMonths, rYears,
                    isFav, isImp, subs, tags, priority,
                    isProjectTask = isProjectTask,
                    projectId = savedProjectId,
                    status = status,
                    comments = comments,
                    attachments = attachments,
                    assignee = assignee,
                    existingTask = taskToEdit
                )
                showAddTask = false
            },
            onDelete = {
                taskToEdit?.let { viewModel.deleteTask(it, context) }
                showAddTask = false
            }
        )
    }

    // Project 3-Button Speed Dial Modal (Tasks, Notes, Templates)
    if (showProjectActionMenu) {
        ModalBottomSheet(
            onDismissRequest = { showProjectActionMenu = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Project Workspace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select what you would like to create:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Button 1: Tasks
                Surface(
                    onClick = {
                        showProjectActionMenu = false
                        taskToEdit = null
                        isAddingProjectTask = true
                        targetProjectIdForAdd = viewModel.selectedProjectId.value
                        showAddTask = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("New project task with checklist, priority & alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 2: Notes
                Surface(
                    onClick = {
                        showProjectActionMenu = false
                        noteToEdit = null
                        showProjectNoteSheet = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.NoteAdd, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Documentation, specs & attach tasks for the project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 3: Templates
                Surface(
                    onClick = {
                        showProjectActionMenu = false
                        showTemplateDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Pre-built workflows (Feature Dev, Bug Fix, Sprint)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Project Note Sheet Modal
    if (showProjectNoteSheet) {
        val allProjTasks by viewModel.allProjectTasks.collectAsState()
        val defaultProjId = viewModel.selectedProjectId.value ?: projectTaskBooks.firstOrNull()?.id ?: 1
        AddProjectNoteSheet(
            projectBooks = projectTaskBooks,
            selectedProjectId = defaultProjId,
            allProjectTasks = allProjTasks,
            existingNote = noteToEdit,
            onDismiss = {
                showProjectNoteSheet = false
                noteToEdit = null
            },
            onSave = { pId, nTitle, nContent, taskIds ->
                viewModel.saveProjectNote(
                    projectId = pId,
                    title = nTitle,
                    content = nContent,
                    attachedTaskIds = taskIds,
                    existingNote = noteToEdit
                )
                showProjectNoteSheet = false
                noteToEdit = null
                Toast.makeText(context, "Note saved!", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                noteToEdit?.let { viewModel.deleteProjectNote(it) }
                showProjectNoteSheet = false
                noteToEdit = null
            },
            onAddNewTaskDirect = { pId, t, p ->
                viewModel.createAndInsertTaskForProject(pId, t, p)
            }
        )
    }

    // Pre-built Project Templates Dialog
    if (showTemplateDialog) {
        val defaultProjId = viewModel.selectedProjectId.value ?: projectTaskBooks.firstOrNull()?.id ?: 1
        ProjectTemplateDialog(
            projects = projectTaskBooks,
            selectedProjectId = defaultProjId,
            onDismiss = { showTemplateDialog = false },
            onSelectTemplate = { pId, templateTitle, templateDesc, templatePriority, templateTags, subtasks ->
                viewModel.createProjectTaskFromTemplate(
                    context = context,
                    projectId = pId,
                    templateTitle = templateTitle,
                    templateDesc = templateDesc,
                    templatePriority = templatePriority,
                    templateTags = templateTags,
                    subtaskList = subtasks
                )
                showTemplateDialog = false
                Toast.makeText(context, "Created task from '$templateTitle' template!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Join Shared Book / List Dialog (LOSE EXP Match)
    if (showJoinSharedListDialog) {
        var joinInput by remember { mutableStateOf("") }
        var isJoining by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isJoining) showJoinSharedListDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Shared List")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a List Invite Code (e.g. PRJ-4K2A), paste a full Sync Token, or scan a QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    joinInput = clip.trim()
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = {
                                handleScanQr()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan QR", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = joinInput,
                        onValueChange = { joinInput = it },
                        placeholder = { Text("Paste code (e.g. PRJ-4K2A) or token here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 5,
                        enabled = !isJoining
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val raw = joinInput.trim()
                        if (raw.isBlank()) {
                            Toast.makeText(context, "Please enter an invite code or token", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isJoining = true
                        handleProcessSharedInput(raw) {
                            isJoining = false
                        }
                    },
                    enabled = !isJoining && joinInput.isNotBlank()
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Join & Sync")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinSharedListDialog = false },
                    enabled = !isJoining
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Universal Spotlight / Command Palette Search Dialog
    if (showOmniSearch) {
        OmniSearchDialog(
            viewModel = viewModel,
            allTasks = allTasks,
            books = taskBooks,
            onDismiss = { showOmniSearch = false },
            onSelectTask = { task ->
                showOmniSearch = false
                taskToEdit = task
                showAddTask = true
            },
            onNavigateToFocus = { task ->
                showOmniSearch = false
                onNavigateToFocus(task)
            },
            onNavigateToAnalytics = {
                showOmniSearch = false
                onNavigateToAnalytics()
            },
            onNavigateToCalendar = {
                showOmniSearch = false
                currentBottomTab = "Calendar"
            }
        )
    }
}

// ProjectsTabContent is implemented in ProjectsScreen.kt

// -------------------------------------------------------------
// Left Navigation Drawer: Microsoft To Do Style Lists Sidebar
// -------------------------------------------------------------
@Composable
fun ListsDrawerContent(
    taskBooks: List<TaskBookEntity>,
    currentBookId: Int,
    pendingTasks: List<TaskEntity>,
    isCreatingList: Boolean,
    newListName: String,
    onNewListNameChange: (String) -> Unit,
    onStartCreateList: () -> Unit,
    onCancelCreateList: () -> Unit,
    onConfirmCreateList: () -> Unit,
    onSelectBook: (Int) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onScanQrCode: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            // Profile row - Tap to open Profile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToProfile),
                color = Color.Transparent
            ) {
                val dNameState by SettingsRepository.userNameFlow.collectAsState()
                val dEmailState by SettingsRepository.userEmailFlow.collectAsState()
                val dName = dNameState ?: "User"
                val dEmail = dEmailState ?: "Active Account"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dName.firstOrNull()?.uppercase() ?: "I", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(dEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Smart Lists (Microsoft To Do style)
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = Color(0xFF2196F3)) },
                label = { Text("My Day") },
                selected = false,
                onClick = { onSelectBook(1) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = Color(0xFFE91E63)) },
                label = { Text("Important") },
                selected = false,
                onClick = { onSelectBook(1) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null, tint = Color(0xFF009688)) },
                label = { Text("Planned") },
                selected = false,
                onClick = { onSelectBook(1) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Home, contentDescription = null, tint = Color(0xFF673AB7)) },
                label = { Text("Tasks") },
                selected = currentBookId == 1,
                badge = {
                    val count = pendingTasks.count { it.bookId == 1 }
                    if (count > 0) Text(count.toString())
                },
                onClick = { onSelectBook(1) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Custom Lists
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(taskBooks.filter { it.id != 1 && !it.isProject }) { book ->
                    val bookCount = pendingTasks.count { it.bookId == book.id }
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        label = { Text(book.name) },
                        selected = currentBookId == book.id,
                        badge = {
                            if (bookCount > 0) Text(bookCount.toString())
                        },
                        onClick = { onSelectBook(book.id ?: 1) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Bottom "+ New list" action
            if (isCreatingList) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = onNewListNameChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New list name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onConfirmCreateList) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCancelCreateList) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStartCreateList)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "New list",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Leaderboard, contentDescription = "Analytics", tint = Color(0xFF8B5CF6)) },
                label = { Text("Productivity Analytics") },
                selected = false,
                onClick = onNavigateToAnalytics,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Timer, contentDescription = "Focus Timer", tint = Color(0xFFF59E0B)) },
                label = { Text("Focus Timer (Pomodoro)") },
                selected = false,
                onClick = onNavigateToFocus,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR", tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Scan QR / Join List") },
                selected = false,
                onClick = onScanQrCode,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label = { Text("Settings") },
                selected = false,
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

// -------------------------------------------------------------
// Advanced Executive Drawer: Profile, Productivity & Settings Hub
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDrawerContent(
    tasks: List<TaskEntity> = emptyList(),
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToCompleted: () -> Unit = {},
    isLiveSyncing: Boolean = false,
    onTriggerSync: () -> Unit = {},
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onLogOut: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out of TaskX?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Are you sure you want to log out? Your tasks are stored safely in cloud sync and you can log back in at any time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalDrawerSheet(
        modifier = Modifier.width(330.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val dNameState by SettingsRepository.userNameFlow.collectAsState()
            val dEmailState by SettingsRepository.userEmailFlow.collectAsState()
            val dName = dNameState?.takeIf { it.isNotBlank() } ?: "User"
            val dEmail = dEmailState?.takeIf { it.isNotBlank() } ?: "Active Account"

            // 1. Executive Gradient Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar with Active Glow
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dName.firstOrNull()?.uppercase() ?: "U",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Online Indicator
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "TaskX Pro",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Tap-to-view Profile
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "View Profile",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Productivity Velocity Metrics Card
                    val completedCount = tasks.count { it.isCompleted }
                    val pendingCount = tasks.count { !it.isCompleted }
                    val totalCount = tasks.size
                    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                    val percentage = (progressRatio * 100).toInt()

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Productivity Velocity",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$percentage% Done",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF10B981),
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "$totalCount",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total Tasks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$completedCount",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$pendingCount",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Power Hub: Quick Navigation Cards
            Text(
                text = "WORKSPACE & PRODUCTIVITY",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Focus Mode Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToFocus),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = "Focus",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Focus",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pomodoro timer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Analytics Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToAnalytics),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Outlined.Leaderboard,
                            contentDescription = "Analytics",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Analytics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Productivity stats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Completed Tasks Tile
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onNavigateToCompleted),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Completed Archive",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${tasks.count { it.isCompleted }} finished tasks stored",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. Cloud Sync Interactive Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLiveSyncing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLiveSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color(0xFF10B981).copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLiveSyncing) Icons.Filled.Refresh else Icons.Filled.CheckCircle,
                            contentDescription = "Sync Status",
                            tint = if (isLiveSyncing) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLiveSyncing) "Syncing with Cloud..." else "Cloud Sync Active",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isLiveSyncing) "Updating real-time data" else "Supabase cloud ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onTriggerSync,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "Sync Now",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 4. Quick Appearance / Theme Mode Segmented Controller
            Text(
                text = "APPEARANCE & THEME",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            val currentTheme by SettingsRepository.themeFlow.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("Light", Icons.Outlined.WbSunny, "Light"),
                    Triple("Dark", Icons.Outlined.Palette, "Dark"),
                    Triple("System", Icons.Outlined.Settings, "Auto")
                ).forEach { (themeKey, icon, label) ->
                    val isSelected = currentTheme.equals(themeKey, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .clickable { SettingsRepository.setTheme(themeKey) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 5. App Settings & Tools
            Text(
                text = "SETTINGS & DATA",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            ListItem(
                headlineContent = { Text("Full Profile & Account", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Avatar, personal stats, and details", style = MaterialTheme.typography.labelSmall) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                modifier = Modifier.clickable(onClick = onNavigateToProfile)
            )

            ListItem(
                headlineContent = { Text("App Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Notifications, reminders, sounds, security", style = MaterialTheme.typography.labelSmall) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    }
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                modifier = Modifier.clickable(onClick = onNavigateToSettings)
            )

            ListItem(
                headlineContent = { Text("Backup Tasks (Export JSON)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Save local JSON database backup", style = MaterialTheme.typography.labelSmall) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = "Export", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.clickable(onClick = onExportData)
            )

            ListItem(
                headlineContent = { Text("Restore Tasks (Import JSON)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Import tasks from saved backup", style = MaterialTheme.typography.labelSmall) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = "Import", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.clickable(onClick = onImportData)
            )

            Spacer(Modifier.height(16.dp))

            // 6. Safe Log Out Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 7. Version & Build Tagline
            Text(
                text = "TaskX • v1.0.0 (Build 2026)\nCrafted for peak productivity",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// -------------------------------------------------------------
// Task Row with Glowing Borders, Statuses, and Comments
// -------------------------------------------------------------
@Composable
fun TaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onStatusChange: (String) -> Unit = {},
    onCommentChange: (String) -> Unit = {},
    onPriorityChange: (Int) -> Unit = {},
    onStartFocus: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val deadline = task.dueTime ?: task.dueDate
    val pLevel = com.example.moderntodo.util.PriorityLevel.fromTask(task)

    // Refined status border color:
    // Overdue: Soft Coral Red
    // Due soon (<= 24h): Soft Amber
    // Normal: Crisp outline variant
    val isOverdue = deadline != null && !task.isCompleted && (deadline - now < 0)
    val isDueSoon = deadline != null && !task.isCompleted && (deadline - now in 0..(24 * 60 * 60 * 1000L))
    val cardBorder = when {
        isOverdue -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
        isDueSoon -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
        else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    var showPriorityMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Complete",
                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
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
                            task.tags.isNotBlank() ||
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
                                        tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = format.format(Date(task.dueDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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

                            if (task.tags.isNotBlank()) {
                                val firstTag = task.tags.split(",").firstOrNull { it.isNotBlank() }?.trim()
                                if (firstTag != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "#$firstTag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
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

                // Right Badges & Quick Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            com.example.moderntodo.util.PriorityLevel.values().forEach { levelOption ->
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

                    // Focus quick action
                    IconButton(
                        onClick = onStartFocus,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = "Focus",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Status menu dialog
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
}

// -------------------------------------------------------------
// Filter & Sort Bottom Sheet
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filterImportant by viewModel.filterImportant.collectAsState()
    val filterFavorite by viewModel.filterFavorite.collectAsState()
    val filterPriority by viewModel.filterPriority.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().navigationBarsPadding()) {
            Text("Filter & Sort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search Title") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Sort Order", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortOrder == SortOrder.DATE_CREATED_DESC, onClick = { viewModel.updateSortOrder(SortOrder.DATE_CREATED_DESC) }, label = { Text("Newest") })
                FilterChip(selected = sortOrder == SortOrder.PRIORITY_ASC, onClick = { viewModel.updateSortOrder(SortOrder.PRIORITY_ASC) }, label = { Text("Priority") })
                FilterChip(selected = sortOrder == SortOrder.TITLE_ASC, onClick = { viewModel.updateSortOrder(SortOrder.TITLE_ASC) }, label = { Text("A-Z") })
                FilterChip(selected = sortOrder == SortOrder.DEADLINE_ASC, onClick = { viewModel.updateSortOrder(SortOrder.DEADLINE_ASC) }, label = { Text("Deadline") })
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Filter by Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterPriority == null,
                    onClick = { viewModel.setFilterPriority(null) },
                    label = { Text("All") }
                )
                com.example.moderntodo.util.PriorityLevel.values().forEach { pLevel ->
                    FilterChip(
                        selected = filterPriority == pLevel.level,
                        onClick = {
                            viewModel.setFilterPriority(if (filterPriority == pLevel.level) null else pLevel.level)
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

            Spacer(modifier = Modifier.height(16.dp))
            Text("Other Filters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterImportant, onClick = { viewModel.toggleFilterImportant() }, label = { Text("Flagged") }, leadingIcon = { if (filterImportant) Icon(Icons.Filled.Flag, null) })
                FilterChip(selected = filterFavorite, onClick = { viewModel.toggleFilterFavorite() }, label = { Text("Favorite") }, leadingIcon = { if (filterFavorite) Icon(Icons.Filled.Star, null) })
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -------------------------------------------------------------
// Add / Edit Project Note Sheet with Attached Project Tasks
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectNoteSheet(
    projectBooks: List<TaskBookEntity>,
    selectedProjectId: Int,
    allProjectTasks: List<TaskEntity>,
    existingNote: ProjectNoteEntity? = null,
    onDismiss: () -> Unit,
    onSave: (projectId: Int, title: String, content: String, attachedTaskIds: List<Int>) -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddNewTaskDirect: (suspend (projectId: Int, title: String, priority: Int) -> Int)? = null
) {
    var activeProjectId by remember { mutableStateOf(existingNote?.projectId ?: selectedProjectId) }
    var title by remember { mutableStateOf(existingNote?.title ?: "") }
    var content by remember { mutableStateOf(existingNote?.content ?: "") }
    val initialAttachedIds = existingNote?.attachedTaskIds?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    val selectedTaskIds = remember { mutableStateListOf<Int>().apply { addAll(initialAttachedIds) } }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val projectTasksForSelected = allProjectTasks.filter { (it.projectId == activeProjectId || it.bookId == activeProjectId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingNote == null) "New Project Note" else "Edit Project Note",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Project Selector (if multiple)
                if (projectBooks.isNotEmpty()) {
                    Text(
                        text = "Project Workspace:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(projectBooks) { proj ->
                            val pId = proj.id ?: 0
                            val isSelected = activeProjectId == pId
                            FilterChip(
                                selected = isSelected,
                                onClick = { activeProjectId = pId },
                                label = { Text(proj.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.Check else Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title *") },
                    placeholder = { Text("e.g. Architecture Overview, Sprint Goals, QA Checklist") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Content Field
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Content & Documentation") },
                    placeholder = { Text("Write project requirements, technical specs, discussion notes...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    maxLines = 12
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Attached Tasks Section
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Attach Project Tasks",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${selectedTaskIds.size} attached",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check the tasks in this project you want to attach and track directly within this note:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (projectTasksForSelected.isEmpty()) {
                            Text(
                                text = "No tasks available in this project yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                projectTasksForSelected.forEach { task ->
                                    val taskId = task.id ?: 0
                                    val isChecked = selectedTaskIds.contains(taskId)
                                    val pLevel = com.example.moderntodo.util.PriorityLevel.fromTask(task)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) selectedTaskIds.remove(taskId) else selectedTaskIds.add(taskId)
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked == true) selectedTaskIds.add(taskId) else selectedTaskIds.remove(taskId)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
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
                            }
                        }

                        // Direct Task Creation Section inside Note Sheet
                        Spacer(modifier = Modifier.height(12.dp))
                        var newDirectTaskTitle by remember { mutableStateOf("") }
                        var newDirectTaskPriority by remember { mutableStateOf(com.example.moderntodo.util.PriorityLevel.HIGH) }
                        var showDirectPriorityMenu by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Add Task Direct to this Note",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newDirectTaskTitle,
                                        onValueChange = { newDirectTaskTitle = it },
                                        placeholder = { Text("Task title...", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        shape = RoundedCornerShape(10.dp),
                                        trailingIcon = {
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = newDirectTaskPriority.color.copy(alpha = 0.15f),
                                                    modifier = Modifier
                                                        .clickable { showDirectPriorityMenu = true }
                                                        .padding(end = 4.dp)
                                                ) {
                                                    Text(
                                                        text = newDirectTaskPriority.code,
                                                        color = newDirectTaskPriority.color,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showDirectPriorityMenu,
                                                    onDismissRequest = { showDirectPriorityMenu = false }
                                                ) {
                                                    com.example.moderntodo.util.PriorityLevel.values().forEach { levelOption ->
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
                                                                newDirectTaskPriority = levelOption
                                                                showDirectPriorityMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val coroutineScope = rememberCoroutineScope()
                                    IconButton(
                                        onClick = {
                                            if (newDirectTaskTitle.isNotBlank() && onAddNewTaskDirect != null) {
                                                val titleToAdd = newDirectTaskTitle.trim()
                                                val pToAdd = newDirectTaskPriority.level
                                                newDirectTaskTitle = ""
                                                coroutineScope.launch {
                                                    val newId = onAddNewTaskDirect(activeProjectId, titleToAdd, pToAdd)
                                                    if (!selectedTaskIds.contains(newId)) {
                                                        selectedTaskIds.add(newId)
                                                    }
                                                }
                                            }
                                        },
                                        enabled = newDirectTaskTitle.isNotBlank() && onAddNewTaskDirect != null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (newDirectTaskTitle.isNotBlank() && onAddNewTaskDirect != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Add Task to Note",
                                            tint = if (newDirectTaskTitle.isNotBlank() && onAddNewTaskDirect != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (existingNote != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(activeProjectId, title.trim(), content.trim(), selectedTaskIds.toList())
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (existingNote == null) "Create Note" else "Save Note", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete note '$title'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
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

// -------------------------------------------------------------
// Pre-built Project Workflow Templates Picker
// -------------------------------------------------------------
data class WorkflowTemplate(
    val title: String,
    val description: String,
    val priority: Int,
    val tags: String,
    val subtasks: List<String>
)

@Composable
fun ProjectTemplateDialog(
    projects: List<TaskBookEntity>,
    selectedProjectId: Int,
    onDismiss: () -> Unit,
    onSelectTemplate: (projectId: Int, title: String, desc: String, priority: Int, tags: String, subtasks: List<String>) -> Unit
) {
    var targetProjId by remember { mutableStateOf(selectedProjectId) }

    val templates = remember {
        listOf(
            WorkflowTemplate(
                title = "Feature Implementation & Delivery",
                description = "End-to-end software feature development lifecycle with code review and QA.",
                priority = 2, // High
                tags = "feature, dev, sprint",
                subtasks = listOf(
                    "Review product requirements & technical specifications",
                    "Design UI mocks and database schema changes",
                    "Implement core feature and application logic",
                    "Write automated unit & UI verification tests",
                    "Peer code review, address comments & merge pull request"
                )
            ),
            WorkflowTemplate(
                title = "Bug Investigation & Hotfix QA",
                description = "Rapid triage, reproduction, patch implementation, and validation workflow.",
                priority = 1, // Urgent
                tags = "bug, hotfix, qa",
                subtasks = listOf(
                    "Reproduce error with reported steps & collect stack trace logs",
                    "Isolate root cause and write regression test case",
                    "Develop and verify bug fix locally",
                    "Code review approval & deploy staging build",
                    "Final QA sign-off & production release"
                )
            ),
            WorkflowTemplate(
                title = "Sprint Planning & Milestone Retrospective",
                description = "Agile sprint kickoff, backlog estimation, mid-sprint sync, and demo.",
                priority = 3, // Medium
                tags = "sprint, agile, planning",
                subtasks = listOf(
                    "Groom sprint backlog and estimate user stories",
                    "Conduct sprint kickoff and assign tickets",
                    "Mid-sprint milestone & blocker checkpoint",
                    "Sprint demo showcase to stakeholders",
                    "Retrospective meeting and process improvements"
                )
            ),
            WorkflowTemplate(
                title = "Client Release & Handover Checklist",
                description = "Production readiness audit, security review, deliverables package, and sign-off.",
                priority = 2, // High
                tags = "client, release, handover",
                subtasks = listOf(
                    "Complete production release candidate build",
                    "Verify security rules and environment credentials",
                    "Package user documentation and release changelog",
                    "Deliver demo walkthrough to client stakeholders",
                    "Collect signed acceptance criteria & archive project"
                )
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Project Task Templates", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (projects.size > 1) {
                    Text("Select Destination Project:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(projects) { proj ->
                            val pId = proj.id ?: 0
                            FilterChip(
                                selected = targetProjId == pId,
                                onClick = { targetProjId = pId },
                                label = { Text(proj.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Pick a pre-configured template to spawn ready-to-run project tasks with full checklists:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                templates.forEach { tmpl ->
                    val pLevel = com.example.moderntodo.util.PriorityLevel.fromLevel(tmpl.priority)
                    Surface(
                        onClick = {
                            onSelectTemplate(
                                targetProjId,
                                tmpl.title,
                                tmpl.description,
                                tmpl.priority,
                                tmpl.tags,
                                tmpl.subtasks
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tmpl.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tmpl.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${tmpl.subtasks.size} checklist steps included",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
