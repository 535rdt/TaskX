package com.example.moderntodo.ui.main

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NextWeek
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.receiver.AlarmReceiver
import com.example.moderntodo.util.PriorityLevel
import com.example.moderntodo.util.ProjectPreferences
import com.example.moderntodo.util.SettingsRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    existingTask: TaskEntity? = null,
    isProjectTaskDefault: Boolean = false,
    projectIdDefault: Int? = null,
    normalTaskBooks: List<TaskBookEntity> = emptyList(),
    projectTaskBooks: List<TaskBookEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        desc: String,
        date: Long?,
        time: Long?,
        alarm: Boolean,
        ringtoneUri: String?,
        repeatMode: String?,
        repeatInterval: Int,
        repeatUnit: String,
        repeatStarts: Long?,
        repeatEndsType: String,
        repeatEndsValue: Long?,
        repeatDaysOfWeek: String?,
        repeatMonths: String?,
        repeatYears: String?,
        isFavorite: Boolean,
        isImportant: Boolean,
        subTasks: String,
        tags: String,
        priority: Int,
        isProjectTask: Boolean,
        projectId: Int?,
        status: String,
        comments: String,
        attachments: String,
        assignee: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val globalNotificationsEnabled by SettingsRepository.notificationsEnabledFlow.collectAsState()
    val globalDefaultRingtoneUri by SettingsRepository.defaultRingtoneUriFlow.collectAsState()

    // 1. Task Core Properties
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var desc by remember { mutableStateOf(existingTask?.description ?: "") }
    var status by remember { mutableStateOf(existingTask?.status ?: "Pending") }
    var comments by remember { mutableStateOf(existingTask?.comments ?: "") }

    // 2. Project vs Normal List Destination
    var isProjectTask by remember {
        mutableStateOf(existingTask?.isProjectTask ?: isProjectTaskDefault)
    }
    var selectedProjectId by remember {
        mutableStateOf(existingTask?.projectId ?: projectIdDefault ?: projectTaskBooks.firstOrNull()?.id)
    }
    var selectedNormalBookId by remember {
        mutableStateOf(existingTask?.bookId ?: normalTaskBooks.firstOrNull()?.id ?: 1)
    }

    // 3. Priority & Flagging
    var priority by remember {
        mutableStateOf(existingTask?.let { PriorityLevel.fromTask(it).level } ?: 4)
    }
    var isImportant by remember { mutableStateOf(existingTask?.isImportant ?: (priority <= 2)) }
    var isFavorite by remember { mutableStateOf(existingTask?.isFavorite ?: false) }

    // 4. Scheduling (Date, Time, Alarm, Sound)
    var date by remember { mutableStateOf<Long?>(existingTask?.dueDate) }
    var time by remember { mutableStateOf<Long?>(existingTask?.dueTime) }
    var alarm by remember { mutableStateOf(existingTask?.hasAlarm ?: (existingTask?.dueTime != null || globalNotificationsEnabled)) }
    var ringtoneUri by remember { mutableStateOf<String?>(existingTask?.ringtoneUri ?: globalDefaultRingtoneUri) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var activeRingtone by remember { mutableStateOf<Ringtone?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRingtone?.stop()
            } catch (_: Exception) {}
        }
    }

    // 5. Sub-tasks Checklist
    val initialSubtasks = existingTask?.subTasks?.split(";;")?.filter { it.isNotBlank() }?.map {
        val parts = it.split("|")
        mutableStateOf(Pair(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "false" }.toBoolean()))
    } ?: emptyList()
    val subTasks = remember {
        androidx.compose.runtime.snapshots.SnapshotStateList<MutableState<Pair<String, Boolean>>>().apply {
            addAll(initialSubtasks)
        }
    }
    var newSubtaskInput by remember { mutableStateOf("") }

    // 6. Tags
    var tagsInput by remember { mutableStateOf(existingTask?.tags ?: "") }

    // 7. Repeat Schedule
    var isRepeating by remember { mutableStateOf(existingTask?.repeatMode != null && existingTask.repeatMode != "None") }
    var repeatInterval by remember { mutableStateOf(existingTask?.repeatInterval ?: 1) }
    var repeatUnit by remember { mutableStateOf(existingTask?.repeatUnit ?: "Days") }
    var repeatStarts by remember { mutableStateOf<Long?>(existingTask?.repeatStarts) }
    var repeatEndsType by remember { mutableStateOf(existingTask?.repeatEndsType ?: "Never") }
    var repeatEndsValue by remember { mutableStateOf<Long?>(existingTask?.repeatEndsValue) }
    var repeatDaysOfWeek by remember { mutableStateOf<String?>(existingTask?.repeatDaysOfWeek) }
    var repeatMonths by remember { mutableStateOf<String?>(existingTask?.repeatMonths) }
    var repeatYears by remember { mutableStateOf<String?>(existingTask?.repeatYears) }
    var showRepeatConfig by remember { mutableStateOf(false) }

    // Confirmation for Delete
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 8. Assignee & Attachments
    var assignee by remember { mutableStateOf(existingTask?.assignee ?: "") }
    var attachments by remember { mutableStateOf(existingTask?.attachments ?: "") }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var newLinkTitle by remember { mutableStateOf("") }
    var newLinkUrl by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Document"
            val newAttachment = "file|$fileName|$uri"
            attachments = if (attachments.isBlank()) newAttachment else "$attachments;;$newAttachment"
            Toast.makeText(context, "Attached: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = showRepeatConfig) {
        showRepeatConfig = false
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.let { data ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
            }
            if (uri != null) {
                ringtoneUri = uri.toString()
                try {
                    activeRingtone?.stop()
                    val r = RingtoneManager.getRingtone(context, uri)
                    activeRingtone = r
                    r?.play()
                    isPlayingPreview = true
                } catch (_: Exception) {}
            } else {
                ringtoneUri = null
            }
        }
    }

    if (showRepeatConfig) {
        RepeatConfigScreen(
            interval = repeatInterval,
            unit = repeatUnit,
            starts = repeatStarts,
            endsType = repeatEndsType,
            endsValue = repeatEndsValue,
            daysOfWeek = repeatDaysOfWeek,
            months = repeatMonths,
            years = repeatYears,
            isRepeating = isRepeating,
            time = time,
            onDismiss = { showRepeatConfig = false },
            onSave = { rIsRepeating, rInterval, rUnit, rStarts, rEndsType, rEndsValue, rDaysOfWeek, rMonths, rYears, rTime ->
                isRepeating = rIsRepeating
                repeatInterval = rInterval
                repeatUnit = rUnit
                repeatStarts = rStarts
                repeatEndsType = rEndsType
                repeatEndsValue = rEndsValue
                repeatDaysOfWeek = rDaysOfWeek
                repeatMonths = rMonths
                repeatYears = rYears
                if (rTime != null) time = rTime
                showRepeatConfig = false
            }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // -------------------------------------------------------------
            // Top App Bar
            // -------------------------------------------------------------
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = if (existingTask == null) "New Task Entry" else "Edit Task Entry",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            isImportant = !isImportant
                            if (isImportant && priority > 2) priority = 1
                        }) {
                            Icon(
                                if (isImportant) Icons.Filled.Flag else Icons.Outlined.OutlinedFlag,
                                contentDescription = "Flagged",
                                tint = if (isImportant) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Main Scrollable Form Body
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Section 1: Task Title & Description
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Task Title *") },
                            placeholder = { Text("What needs to be done?") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            trailingIcon = {
                                if (title.isNotEmpty()) {
                                    IconButton(onClick = { title = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            label = { Text("Notes & Description (Optional)") },
                            placeholder = { Text("Add details, checklists, context or links...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            maxLines = 6
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Destination Workspace (Personal vs Project)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Destination Workspace",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isProjectTask,
                                onClick = { isProjectTask = false },
                                label = { Text("Personal List") },
                                leadingIcon = { Icon(Icons.Outlined.Checklist, null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isProjectTask,
                                onClick = { isProjectTask = true },
                                label = { Text("Project Workspace") },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isProjectTask) {
                            if (projectTaskBooks.isEmpty()) {
                                Text(
                                    text = "No projects created yet. Go to Projects tab to create one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Assign to Project:", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(projectTaskBooks) { project ->
                                        val projId = project.id ?: 0
                                        val isSelected = selectedProjectId == projId
                                        val colorHex = ProjectPreferences.getColorHex(projId)
                                        val projColor = try {
                                            Color(android.graphics.Color.parseColor(colorHex))
                                        } catch (e: Exception) {
                                            MaterialTheme.colorScheme.primary
                                        }

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedProjectId = projId },
                                            label = { Text(project.name) },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(projColor)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = projColor.copy(alpha = 0.2f),
                                                selectedLabelColor = projColor
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            if (normalTaskBooks.isNotEmpty()) {
                                Text("List:", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(normalTaskBooks) { book ->
                                        val bId = book.id ?: 1
                                        val isSelected = selectedNormalBookId == bId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedNormalBookId = bId },
                                            label = { Text(book.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: 4-Tier Color Priority Level
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Priority Level",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val currLevel = PriorityLevel.fromLevel(priority)
                            Text(
                                text = "${currLevel.code} • ${currLevel.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = currLevel.color,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriorityLevel.values().forEach { pLevel ->
                                val isSelected = priority == pLevel.level
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            priority = pLevel.level
                                            isImportant = pLevel.level <= 2
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) pLevel.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) BorderStroke(2.dp, pLevel.color) else null
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Flag else Icons.Outlined.OutlinedFlag,
                                            contentDescription = pLevel.label,
                                            tint = if (isSelected) pLevel.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = pLevel.code,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) pLevel.color else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = pLevel.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) pLevel.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = PriorityLevel.fromLevel(priority).description,
                            style = MaterialTheme.typography.bodySmall,
                            color = PriorityLevel.fromLevel(priority).color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 4: Smart Due Date & Time Scheduling
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Schedule & Deadlines",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (date != null || time != null) {
                                TextButton(onClick = {
                                    date = null
                                    time = null
                                    alarm = false
                                }) {
                                    Text("Clear Schedule", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Date Presets
                        Text("Quick Date Presets:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.HOUR_OF_DAY, 23)
                                        cal.set(Calendar.MINUTE, 59)
                                        date = cal.timeInMillis
                                    },
                                    label = { Text("Today") },
                                    leadingIcon = { Icon(Icons.Outlined.Today, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, 1)
                                        cal.set(Calendar.HOUR_OF_DAY, 23)
                                        cal.set(Calendar.MINUTE, 59)
                                        date = cal.timeInMillis
                                    },
                                    label = { Text("Tomorrow") },
                                    leadingIcon = { Icon(Icons.Outlined.WbSunny, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        val daysUntilSaturday = (Calendar.SATURDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                                        cal.add(Calendar.DAY_OF_YEAR, if (daysUntilSaturday == 0) 7 else daysUntilSaturday)
                                        cal.set(Calendar.HOUR_OF_DAY, 23)
                                        cal.set(Calendar.MINUTE, 59)
                                        date = cal.timeInMillis
                                    },
                                    label = { Text("Weekend") },
                                    leadingIcon = { Icon(Icons.Outlined.Weekend, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        val daysUntilMonday = (Calendar.MONDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                                        cal.add(Calendar.DAY_OF_YEAR, if (daysUntilMonday == 0) 7 else daysUntilMonday)
                                        cal.set(Calendar.HOUR_OF_DAY, 23)
                                        cal.set(Calendar.MINUTE, 59)
                                        date = cal.timeInMillis
                                    },
                                    label = { Text("Next Week") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NextWeek, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Time Presets
                        Text(
                            text = "Quick Time Presets",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                AssistChip(
                                    onClick = {
                                        val c = Calendar.getInstance()
                                        if (date != null) c.timeInMillis = date!!
                                        c.set(Calendar.HOUR_OF_DAY, 9)
                                        c.set(Calendar.MINUTE, 0)
                                        c.set(Calendar.SECOND, 0)
                                        time = c.timeInMillis
                                        if (date == null) date = c.timeInMillis
                                        alarm = true
                                    },
                                    label = { Text("9:00 AM") },
                                    leadingIcon = { Icon(Icons.Outlined.WbSunny, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val c = Calendar.getInstance()
                                        if (date != null) c.timeInMillis = date!!
                                        c.set(Calendar.HOUR_OF_DAY, 13)
                                        c.set(Calendar.MINUTE, 0)
                                        c.set(Calendar.SECOND, 0)
                                        time = c.timeInMillis
                                        if (date == null) date = c.timeInMillis
                                        alarm = true
                                    },
                                    label = { Text("1:00 PM") },
                                    leadingIcon = { Icon(Icons.Outlined.LightMode, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val c = Calendar.getInstance()
                                        if (date != null) c.timeInMillis = date!!
                                        c.set(Calendar.HOUR_OF_DAY, 18)
                                        c.set(Calendar.MINUTE, 0)
                                        c.set(Calendar.SECOND, 0)
                                        time = c.timeInMillis
                                        if (date == null) date = c.timeInMillis
                                        alarm = true
                                    },
                                    label = { Text("6:00 PM") },
                                    leadingIcon = { Icon(Icons.Outlined.NightsStay, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val c = Calendar.getInstance()
                                        if (date != null) c.timeInMillis = date!!
                                        c.set(Calendar.HOUR_OF_DAY, 21)
                                        c.set(Calendar.MINUTE, 0)
                                        c.set(Calendar.SECOND, 0)
                                        time = c.timeInMillis
                                        if (date == null) date = c.timeInMillis
                                        alarm = true
                                    },
                                    label = { Text("9:00 PM") },
                                    leadingIcon = { Icon(Icons.Outlined.Bedtime, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val c = Calendar.getInstance()
                                        c.add(Calendar.HOUR_OF_DAY, 1)
                                        c.set(Calendar.SECOND, 0)
                                        time = c.timeInMillis
                                        date = c.timeInMillis
                                        alarm = true
                                    },
                                    label = { Text("+1 Hour") },
                                    leadingIcon = { Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom Date & Time Picker Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    if (date != null) calendar.timeInMillis = date!!
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val c = Calendar.getInstance()
                                            c.set(y, m, d)
                                            date = c.timeInMillis
                                            if (time != null) {
                                                val tCal = Calendar.getInstance().apply { timeInMillis = time!! }
                                                c.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
                                                c.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
                                                c.set(Calendar.SECOND, 0)
                                                time = c.timeInMillis
                                            }
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (date == null) "Set Date" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(date!!)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    if (time != null) calendar.timeInMillis = time!!
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            val c = Calendar.getInstance()
                                            if (date != null) c.timeInMillis = date!!
                                            c.set(Calendar.HOUR_OF_DAY, h)
                                            c.set(Calendar.MINUTE, min)
                                            c.set(Calendar.SECOND, 0)
                                            time = c.timeInMillis
                                            alarm = true
                                            if (date == null) date = c.timeInMillis
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (time == null) "Set Time" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time!!)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val areSysNotificationsOn = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
                        if (!areSysNotificationsOn) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Notifications are blocked by your phone. Tap here to enable in Settings.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Subtle hint when time is not yet set
                        if (time == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Set a time above to configure custom notification sound",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // CUSTOM NOTIFICATION SOUND FUNCTION (Appears when time is set!)
                        AnimatedVisibility(
                            visible = time != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        // Header Row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Filled.NotificationsActive,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Custom Notification Sound",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val timeFormatted = if (time != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time!!)) else ""
                                                val dateFormatted = if (date != null) SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(date!!)) else "Today"
                                                Text(
                                                    text = "🔔 Rings on $dateFormatted at $timeFormatted",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    time = null
                                                    try { activeRingtone?.stop() } catch (_: Exception) {}
                                                    isPlayingPreview = false
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Clear time",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Current Sound Tile with Preview & Change
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 1.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.MusicNote,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = getRingtoneTitle(context, ringtoneUri),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (ringtoneUri != null) "Custom Task Alert Tone" else "System Default Notification",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                // Play / Stop Preview button
                                                FilledTonalButton(
                                                    onClick = {
                                                        if (isPlayingPreview) {
                                                            try { activeRingtone?.stop() } catch (_: Exception) {}
                                                            isPlayingPreview = false
                                                            activeRingtone = null
                                                        } else {
                                                            try {
                                                                activeRingtone?.stop()
                                                                val uri = if (!ringtoneUri.isNullOrBlank()) {
                                                                    Uri.parse(ringtoneUri)
                                                                } else {
                                                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                                                }
                                                                val r = RingtoneManager.getRingtone(context, uri)
                                                                activeRingtone = r
                                                                r?.play()
                                                                isPlayingPreview = true
                                                            } catch (_: Exception) {
                                                                isPlayingPreview = false
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        if (isPlayingPreview) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                                        contentDescription = if (isPlayingPreview) "Stop" else "Preview",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isPlayingPreview) "Stop" else "Listen", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Quick Sound Selection Chips
                                        Text(
                                            text = "Quick Sound Presets:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            item {
                                                FilterChip(
                                                    selected = ringtoneUri == null,
                                                    onClick = {
                                                        ringtoneUri = null
                                                        try {
                                                            activeRingtone?.stop()
                                                            val r = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                                            activeRingtone = r
                                                            r?.play()
                                                            isPlayingPreview = true
                                                        } catch (_: Exception) {}
                                                    },
                                                    label = { Text("Default Tone") },
                                                    leadingIcon = { Icon(Icons.Outlined.Notifications, null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = ringtoneUri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString(),
                                                    onClick = {
                                                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
                                                        ringtoneUri = uri
                                                        try {
                                                            activeRingtone?.stop()
                                                            if (uri != null) {
                                                                val r = RingtoneManager.getRingtone(context, Uri.parse(uri))
                                                                activeRingtone = r
                                                                r?.play()
                                                                isPlayingPreview = true
                                                            }
                                                        } catch (_: Exception) {}
                                                    },
                                                    label = { Text("Alarm Ring") },
                                                    leadingIcon = { Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = ringtoneUri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.toString(),
                                                    onClick = {
                                                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.toString()
                                                        ringtoneUri = uri
                                                        try {
                                                            activeRingtone?.stop()
                                                            if (uri != null) {
                                                                val r = RingtoneManager.getRingtone(context, Uri.parse(uri))
                                                                activeRingtone = r
                                                                r?.play()
                                                                isPlayingPreview = true
                                                            }
                                                        } catch (_: Exception) {}
                                                    },
                                                    label = { Text("Phone Ringtone") },
                                                    leadingIcon = { Icon(Icons.Outlined.RingVolume, null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                AssistChip(
                                                    onClick = {
                                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                                            if (!ringtoneUri.isNullOrBlank()) {
                                                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringtoneUri))
                                                            }
                                                        }
                                                        ringtonePickerLauncher.launch(intent)
                                                    },
                                                    label = { Text("Browse Device Sounds...") },
                                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Alert Mode & Test Notification
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Switch(
                                                    checked = alarm,
                                                    onCheckedChange = { alarm = it }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = if (alarm) "Full Alarm Alert" else "Notification Only",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = if (alarm) "Full-screen alert" else "Sound + Banner",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    AlarmReceiver.showNotification(
                                                        context = context,
                                                        taskId = 9999,
                                                        title = title.ifBlank { "Task Reminder" },
                                                        desc = if (desc.isNotBlank()) desc else "Custom notification sound preview",
                                                        ringtoneUriStr = ringtoneUri,
                                                        hasAlarm = false
                                                    )
                                                    Toast.makeText(context, "Playing custom notification sound...", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Outlined.NotificationsActive, null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Test Alert", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Repeat Configuration with ON/OFF Switch & Quick Presets
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRepeating) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isRepeating) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Repeat,
                                            contentDescription = null,
                                            tint = if (isRepeating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Repeat Task",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val repeatLabel = if (isRepeating) "Repeats every $repeatInterval $repeatUnit" else "Repeat is OFF (one-time task)"
                                            Text(
                                                text = repeatLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isRepeating,
                                        onCheckedChange = { checked ->
                                            isRepeating = checked
                                            if (checked) {
                                                if (repeatInterval <= 0) repeatInterval = 1
                                                if (repeatUnit.isBlank()) repeatUnit = "Days"
                                            }
                                        }
                                    )
                                }

                                if (isRepeating) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Quick Presets:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = repeatInterval == 1 && repeatUnit == "Days" && repeatDaysOfWeek == null,
                                            onClick = {
                                                repeatInterval = 1
                                                repeatUnit = "Days"
                                                repeatDaysOfWeek = null
                                            },
                                            label = { Text("Daily", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = repeatInterval == 1 && repeatUnit == "Weeks" && repeatDaysOfWeek == null,
                                            onClick = {
                                                repeatInterval = 1
                                                repeatUnit = "Weeks"
                                                repeatDaysOfWeek = null
                                            },
                                            label = { Text("Weekly", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = repeatInterval == 1 && repeatUnit == "Months",
                                            onClick = {
                                                repeatInterval = 1
                                                repeatUnit = "Months"
                                                repeatDaysOfWeek = null
                                            },
                                            label = { Text("Monthly", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = repeatDaysOfWeek == "Mon,Tue,Wed,Thu,Fri",
                                            onClick = {
                                                repeatInterval = 1
                                                repeatUnit = "Weeks"
                                                repeatDaysOfWeek = "Mon,Tue,Wed,Thu,Fri"
                                            },
                                            label = { Text("Weekdays", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = { showRepeatConfig = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Custom Repeat Schedule...", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 5: Sub-tasks Checklist with Live Progress
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val totalSubtasks = subTasks.size
                        val completedSubtasks = subTasks.count { it.value.second }
                        val progress = if (totalSubtasks > 0) completedSubtasks.toFloat() / totalSubtasks else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sub-tasks & Checklist",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (totalSubtasks > 0) {
                                Text(
                                    text = "$completedSubtasks / $totalSubtasks done (${(progress * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (progress == 1f) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (totalSubtasks > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (progress == 1f) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtask items
                        subTasks.forEachIndexed { index, subTaskState ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = subTaskState.value.second,
                                    onCheckedChange = { checked ->
                                        subTaskState.value = subTaskState.value.copy(second = checked)
                                    }
                                )
                                OutlinedTextField(
                                    value = subTaskState.value.first,
                                    onValueChange = { newTitle ->
                                        subTaskState.value = subTaskState.value.copy(first = newTitle)
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    placeholder = { Text("Sub-task step...") },
                                    textStyle = if (subTaskState.value.second) {
                                        MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    }
                                )
                                IconButton(onClick = { subTasks.removeAt(index) }) {
                                    Icon(Icons.Filled.Close, "Remove Sub-task", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Add Sub-task field
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newSubtaskInput,
                                onValueChange = { newSubtaskInput = it },
                                placeholder = { Text("Add next sub-task step...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newSubtaskInput.isNotBlank()) {
                                            subTasks.add(mutableStateOf(Pair(newSubtaskInput.trim(), false)))
                                            newSubtaskInput = ""
                                        }
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalIconButton(
                                onClick = {
                                    if (newSubtaskInput.isNotBlank()) {
                                        subTasks.add(mutableStateOf(Pair(newSubtaskInput.trim(), false)))
                                        newSubtaskInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add step")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 6: Workflow Status
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Workflow Status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Pending", "Working", "Full Complete").forEach { st ->
                                FilterChip(
                                    selected = status == st,
                                    onClick = { status = st },
                                    label = { Text(st) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 7: Tags & Work Classification
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tags & Work Labels",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick tag suggestion chips
                        val suggestedTags = listOf("Work", "Personal", "Urgent", "Meeting", "Design", "Dev", "Finance")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suggestedTags) { tag ->
                                val existingTags = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val isTagAdded = existingTags.contains(tag)
                                FilterChip(
                                    selected = isTagAdded,
                                    onClick = {
                                        tagsInput = if (isTagAdded) {
                                            existingTags.filter { it != tag }.joinToString(", ")
                                        } else {
                                            if (tagsInput.isBlank()) tag else "$tagsInput, $tag"
                                        }
                                    },
                                    label = { Text("#$tag") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tagsInput,
                            onValueChange = { tagsInput = it },
                            placeholder = { Text("Custom tags (e.g. #marketing, #v2)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.NewLabel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 8: Activity Comments & Discussion Notes
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Comments & Activity Log",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = comments,
                            onValueChange = { comments = it },
                            placeholder = { Text("Add any initial comments, progress updates or links...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 70.dp),
                            maxLines = 4
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 9: Assignee & Delegation
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Task Assignee / Owner",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (assignee.isNotBlank()) {
                                AssistChip(
                                    onClick = { assignee = "" },
                                    label = { Text("Clear", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Team Member suggestions
                        val suggestedAssignees = listOf("Myself", "Alex (Lead)", "Sarah (Design)", "Dev Team", "QA Tester", "Client")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suggestedAssignees) { name ->
                                val isSelected = assignee.equals(name, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        assignee = if (isSelected) "" else name
                                    },
                                    label = { Text(name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Check else Icons.Outlined.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = assignee,
                            onValueChange = { assignee = it },
                            placeholder = { Text("Assign to person, email, or department...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 10: Attachments & Resources
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Attachments & Resources",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val attachmentItems = attachments.split(";;").filter { it.isNotBlank() }
                            if (attachmentItems.isNotEmpty()) {
                                Text(
                                    text = "${attachmentItems.size} attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: Attach File / Photo, Add Web Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    filePickerLauncher.launch("*/*")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add File / Image", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            OutlinedButton(
                                onClick = {
                                    newLinkTitle = ""
                                    newLinkUrl = ""
                                    showAddLinkDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Web Link", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Attachments List Display
                        val attachmentList = attachments.split(";;").filter { it.isNotBlank() }
                        if (attachmentList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                attachmentList.forEachIndexed { index, rawItem ->
                                    val parts = rawItem.split("|")
                                    val type = parts.getOrNull(0) ?: "file"
                                    val name = parts.getOrNull(1) ?: "Attachment"
                                    val uriStr = parts.getOrNull(2) ?: ""

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (type == "link") Icons.Outlined.Language else Icons.Outlined.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (uriStr.isNotBlank()) {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
                                                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (uriStr.isNotBlank()) {
                                                    Text(
                                                        text = uriStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    val updatedList = attachmentList.toMutableList().apply { removeAt(index) }
                                                    attachments = updatedList.joinToString(";;")
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // -------------------------------------------------------------
            // Bottom Sticky Action Bar
            // -------------------------------------------------------------
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (existingTask != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(52.dp),
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
                                val rMode = if (isRepeating) {
                                    if (repeatDaysOfWeek != null && repeatDaysOfWeek!!.isNotBlank()) "Weekdays"
                                    else when (repeatUnit) {
                                        "Weeks" -> if (repeatInterval == 1) "Weekly" else "Every $repeatInterval Weeks"
                                        "Months" -> if (repeatInterval == 1) "Monthly" else "Every $repeatInterval Months"
                                        "Years" -> if (repeatInterval == 1) "Yearly" else "Every $repeatInterval Years"
                                        else -> if (repeatInterval == 1) "Daily" else "Every $repeatInterval Days"
                                    }
                                } else null
                                val serializedSubTasks = subTasks.map { "${it.value.first}|${it.value.second}" }.joinToString(";;")
                                onSave(
                                    title.trim(),
                                    desc.trim(),
                                    date,
                                    time,
                                    alarm,
                                    ringtoneUri,
                                    rMode,
                                    repeatInterval,
                                    repeatUnit,
                                    repeatStarts,
                                    repeatEndsType,
                                    repeatEndsValue,
                                    repeatDaysOfWeek,
                                    repeatMonths,
                                    repeatYears,
                                    isFavorite,
                                    isImportant,
                                    serializedSubTasks,
                                    tagsInput.trim(),
                                    priority,
                                    isProjectTask,
                                    if (isProjectTask) selectedProjectId else selectedNormalBookId,
                                    status,
                                    comments.trim(),
                                    attachments.trim(),
                                    assignee.trim()
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp),
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (existingTask == null) Icons.Filled.Add else Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (existingTask == null) "Create Task" else "Update Task",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to permanently delete '${title.ifBlank { "this task" }}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Link Dialog
    if (showAddLinkDialog) {
        AlertDialog(
            onDismissRequest = { showAddLinkDialog = false },
            title = { Text("Add Web Link") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newLinkTitle,
                        onValueChange = { newLinkTitle = it },
                        label = { Text("Link Title (Optional)") },
                        placeholder = { Text("e.g. Figma Design, GitHub PR") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newLinkUrl,
                        onValueChange = { newLinkUrl = it },
                        label = { Text("URL / Web Address *") },
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLinkUrl.isNotBlank()) {
                            val cleanUrl = if (!newLinkUrl.startsWith("http://") && !newLinkUrl.startsWith("https://")) {
                                "https://$newLinkUrl"
                            } else newLinkUrl
                            val itemTitle = if (newLinkTitle.isNotBlank()) newLinkTitle.trim() else cleanUrl
                            val item = "link|$itemTitle|$cleanUrl"
                            attachments = if (attachments.isBlank()) item else "$attachments;;$item"
                            showAddLinkDialog = false
                        }
                    },
                    enabled = newLinkUrl.isNotBlank()
                ) {
                    Text("Add Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Repeat Config Subscreen
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatConfigScreen(
    interval: Int,
    unit: String,
    starts: Long?,
    endsType: String,
    endsValue: Long?,
    daysOfWeek: String?,
    months: String?,
    years: String?,
    isRepeating: Boolean,
    time: Long?,
    onDismiss: () -> Unit,
    onSave: (isRepeating: Boolean, interval: Int, unit: String, starts: Long?, endsType: String, endsValue: Long?, daysOfWeek: String?, months: String?, years: String?, time: Long?) -> Unit
) {
    var cInterval by remember { mutableStateOf(interval.toString()) }
    var cUnit by remember { mutableStateOf(unit) }
    var cStarts by remember { mutableStateOf(starts ?: System.currentTimeMillis()) }
    var cEndsType by remember { mutableStateOf(endsType) }
    var cEndsValue by remember { mutableStateOf(endsValue) }
    var cDaysOfWeek by remember { mutableStateOf(daysOfWeek ?: "") }
    var cMonths by remember { mutableStateOf(months ?: "") }
    var cYears by remember { mutableStateOf(years ?: "") }
    var cTime by remember { mutableStateOf(time) }
    var cRepeating by remember { mutableStateOf(true) }

    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text(
                    text = "Repeat Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    onSave(
                        cRepeating,
                        cInterval.toIntOrNull() ?: 1,
                        cUnit,
                        cStarts,
                        cEndsType,
                        cEndsValue,
                        cDaysOfWeek.ifBlank { null },
                        cMonths.ifBlank { null },
                        cYears.ifBlank { null },
                        cTime
                    )
                }) {
                    Text("Done", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text("Repeat Frequency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = cInterval,
                        onValueChange = { if (it.all { char -> char.isDigit() }) cInterval = it },
                        label = { Text("Every") },
                        modifier = Modifier.width(90.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = cUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency Unit") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("Days", "Weeks", "Months", "Years").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        cUnit = u
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Starts On", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = cStarts }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val c = Calendar.getInstance().apply { set(y, m, d) }
                            cStarts = c.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(cStarts)))
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Ends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("Never", "On Date", "After occurrences").forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cEndsType = type }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = cEndsType == type, onClick = { cEndsType = type })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(type)
                    }
                }

                if (cEndsType == "On Date") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = cEndsValue ?: System.currentTimeMillis() }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val c = Calendar.getInstance().apply { set(y, m, d) }
                                cEndsValue = c.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text(if (cEndsValue != null) SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(cEndsValue!!)) else "Select End Date")
                    }
                } else if (cEndsType == "After occurrences") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = (cEndsValue ?: 1).toString(),
                        onValueChange = { cEndsValue = it.toLongOrNull() ?: 1L },
                        label = { Text("Number of times") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(160.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun getRingtoneTitle(context: Context, uriStr: String?): String {
    if (uriStr.isNullOrBlank()) return "Default Notification"
    return try {
        val uri = Uri.parse(uriStr)
        val r = RingtoneManager.getRingtone(context, uri)
        val title = r?.getTitle(context)
        if (!title.isNullOrBlank()) title else "Custom Sound"
    } catch (_: Exception) {
        "Custom Sound"
    }
}
