package com.example.moderntodo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.data.local.ProjectNoteEntity
import com.example.moderntodo.repository.TaskRepository
import android.content.Context
import com.example.moderntodo.util.AlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class SortOrder {
    DATE_CREATED_DESC, DATE_CREATED_ASC, TITLE_ASC, DEADLINE_ASC, PRIORITY_ASC
}

class TaskViewModel(val repository: TaskRepository) : ViewModel() {

    fun exportTasks(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tasks = repository.getAllTasksForExport()
                val json = com.google.gson.Gson().toJson(tasks)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export successful", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importTasks(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    val type = object : com.google.gson.reflect.TypeToken<List<TaskEntity>>() {}.type
                    val tasks: List<TaskEntity> = com.google.gson.Gson().fromJson(json, type)
                    repository.importTasks(tasks)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    val taskBooks: StateFlow<List<TaskBookEntity>> = repository.allTaskBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val normalTaskBooks: StateFlow<List<TaskBookEntity>> = repository.normalTaskBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectTaskBooks: StateFlow<List<TaskBookEntity>> = repository.projectTaskBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingTasks: StateFlow<List<TaskEntity>> = repository.allPendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNormalPendingTasks: StateFlow<List<TaskEntity>> = repository.allNormalPendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjectTasks: StateFlow<List<TaskEntity>> = repository.allProjectTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjectPendingTasks: StateFlow<List<TaskEntity>> = repository.allProjectPendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentBookId = MutableStateFlow(1)
    val currentBookId: StateFlow<Int> = _currentBookId.asStateFlow()

    val isLiveSyncing = com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.isLiveSyncing
    val lastSyncTimestamp = com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.lastSyncTimestamp

    fun triggerManualSync(
        context: Context,
        bookId: Int,
        onComplete: ((com.example.moderntodo.data.remote.SyncResult) -> Unit)? = null
    ) {
        com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.triggerManualSync(context, repository, bookId, onComplete)
    }

    init {
        viewModelScope.launch {
            normalTaskBooks.collect { books ->
                if (books.isNotEmpty() && books.none { it.id == _currentBookId.value }) {
                    val firstId = books.first().id ?: 1
                    _currentBookId.value = firstId
                    com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.setActiveBook(firstId)
                }
            }
        }
    }
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_CREATED_DESC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _filterImportant = MutableStateFlow(false)
    val filterImportant = _filterImportant.asStateFlow()

    private val _filterFavorite = MutableStateFlow(false)
    val filterFavorite = _filterFavorite.asStateFlow()

    private val _filterPriority = MutableStateFlow<Int?>(null)
    val filterPriority = _filterPriority.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun toggleFilterImportant() { _filterImportant.value = !_filterImportant.value }
    fun toggleFilterFavorite() { _filterFavorite.value = !_filterFavorite.value }
    fun setFilterPriority(priority: Int?) { _filterPriority.value = priority }

    private data class FilterCriteria(
        val query: String,
        val sort: SortOrder,
        val imp: Boolean,
        val fav: Boolean,
        val priority: Int?
    )

    private val filterCriteria = combine(
        _searchQuery,
        _sortOrder,
        _filterImportant,
        _filterFavorite,
        _filterPriority
    ) { query, sort, imp, fav, priority ->
        FilterCriteria(query, sort, imp, fav, priority)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingTasks: StateFlow<List<TaskEntity>> = combine(
        _currentBookId.flatMapLatest { bookId -> repository.getNormalPendingTasks(bookId) },
        filterCriteria
    ) { tasks, criteria ->
        var filtered = tasks.filter { !it.isProjectTask }
        if (criteria.query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(criteria.query, ignoreCase = true) }
        }
        if (criteria.imp) filtered = filtered.filter { it.isImportant }
        if (criteria.fav) filtered = filtered.filter { it.isFavorite }
        if (criteria.priority != null) {
            filtered = filtered.filter { com.example.moderntodo.util.PriorityLevel.fromTask(it).level == criteria.priority }
        }
        
        when (criteria.sort) {
            SortOrder.DATE_CREATED_DESC -> filtered.sortedByDescending { (it.id ?: 1) }
            SortOrder.DATE_CREATED_ASC -> filtered.sortedBy { (it.id ?: 1) }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.DEADLINE_ASC -> filtered.sortedBy { it.dueDate ?: Long.MAX_VALUE }
            SortOrder.PRIORITY_ASC -> filtered.sortedBy { com.example.moderntodo.util.PriorityLevel.fromTask(it).level }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------
    // Project Tasks Management & Filters
    // -------------------------------------------------------------
    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId = _selectedProjectId.asStateFlow()

    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery = _projectSearchQuery.asStateFlow()

    private val _projectStatusFilter = MutableStateFlow("All")
    val projectStatusFilter = _projectStatusFilter.asStateFlow()

    private val _projectPriorityFilter = MutableStateFlow<Int?>(null)
    val projectPriorityFilter = _projectPriorityFilter.asStateFlow()

    private val _projectSortOrder = MutableStateFlow(SortOrder.PRIORITY_ASC)
    val projectSortOrder = _projectSortOrder.asStateFlow()

    fun selectProjectFilter(projectId: Int?) {
        _selectedProjectId.value = projectId
        com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.setActiveBook(projectId ?: _currentBookId.value)
    }

    fun updateProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
    }

    fun updateProjectStatusFilter(status: String) {
        _projectStatusFilter.value = status
    }

    fun setProjectPriorityFilter(priority: Int?) {
        _projectPriorityFilter.value = priority
    }

    fun updateProjectSortOrder(sort: SortOrder) {
        _projectSortOrder.value = sort
    }

    private data class ProjectFilterCriteria(
        val projectId: Int?,
        val query: String,
        val status: String,
        val priority: Int?,
        val sort: SortOrder
    )

    private val projectFilterCriteria = combine(
        _selectedProjectId,
        _projectSearchQuery,
        _projectStatusFilter,
        _projectPriorityFilter,
        _projectSortOrder
    ) { projId, query, status, priority, sort ->
        ProjectFilterCriteria(projId, query, status, priority, sort)
    }

    val projectTasks: StateFlow<List<TaskEntity>> = combine(
        repository.allProjectTasks,
        projectFilterCriteria
    ) { tasks, filter ->
        var list = tasks.filter { it.isProjectTask }
        if (filter.projectId != null) {
            list = list.filter { it.projectId == filter.projectId || it.bookId == filter.projectId }
        }
        if (filter.query.isNotBlank()) {
            list = list.filter {
                it.title.contains(filter.query, ignoreCase = true) ||
                it.description.contains(filter.query, ignoreCase = true)
            }
        }
        if (filter.status != "All") {
            list = when (filter.status) {
                "Pending" -> list.filter { !it.isCompleted && (it.status == "Pending" || it.status == null) }
                "Working" -> list.filter { !it.isCompleted && it.status == "Working" }
                "Completed" -> list.filter { it.isCompleted || it.status == "Full Complete" }
                else -> list.filter { it.status == filter.status }
            }
        }
        if (filter.priority != null) {
            list = list.filter { com.example.moderntodo.util.PriorityLevel.fromTask(it).level == filter.priority }
        }

        when (filter.sort) {
            SortOrder.PRIORITY_ASC -> list.sortedBy { com.example.moderntodo.util.PriorityLevel.fromTask(it).level }
            SortOrder.DEADLINE_ASC -> list.sortedBy { it.dueDate ?: Long.MAX_VALUE }
            SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOrder.DATE_CREATED_DESC -> list.sortedByDescending { it.id ?: 0 }
            SortOrder.DATE_CREATED_ASC -> list.sortedBy { it.id ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: kotlinx.coroutines.flow.StateFlow<List<com.example.moderntodo.data.local.TaskEntity>> = repository.completedTasks
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjectNotes: StateFlow<List<ProjectNoteEntity>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectNotes: StateFlow<List<ProjectNoteEntity>> = combine(
        allProjectNotes,
        _selectedProjectId
    ) { notes, selectedProjId ->
        if (selectedProjId != null) {
            notes.filter { it.projectId == selectedProjId }
        } else {
            notes
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProjectNote(
        projectId: Int,
        title: String,
        content: String,
        attachedTaskIds: List<Int>,
        existingNote: ProjectNoteEntity? = null
    ) {
        viewModelScope.launch {
            val idsString = attachedTaskIds.joinToString(",")
            if (existingNote != null) {
                repository.updateNote(
                    existingNote.copy(
                        projectId = projectId,
                        title = title,
                        content = content,
                        attachedTaskIds = idsString,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                repository.insertNote(
                    ProjectNoteEntity(
                        projectId = projectId,
                        title = title,
                        content = content,
                        attachedTaskIds = idsString,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                projectId
            )
        }
    }

    fun deleteProjectNote(note: ProjectNoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                note.projectId
            )
        }
    }

    suspend fun createAndInsertTaskForProject(
        projectId: Int,
        title: String,
        priority: Int = 2
    ): Int {
        val syncId = "task_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val newTask = TaskEntity(
            syncId = syncId,
            title = title.trim(),
            priority = priority,
            isImportant = priority <= 2,
            bookId = projectId,
            isProjectTask = true,
            projectId = projectId
        )
        val newId = repository.insertTask(newTask).toInt()
        com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
            com.example.moderntodo.ModernToDoApp.instance,
            repository,
            projectId
        )
        return newId
    }

    fun addTaskDirectToNote(
        note: ProjectNoteEntity,
        taskTitle: String,
        priority: Int = 2
    ) {
        viewModelScope.launch {
            if (taskTitle.isBlank()) return@launch
            val newId = createAndInsertTaskForProject(note.projectId, taskTitle, priority)
            val currentIds = note.attachedTaskIds.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
            if (!currentIds.contains(newId)) {
                currentIds.add(newId)
            }
            repository.updateNote(
                note.copy(
                    attachedTaskIds = currentIds.joinToString(","),
                    updatedAt = System.currentTimeMillis()
                )
            )
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                note.projectId
            )
        }
    }

    fun createProjectTaskFromTemplate(
        context: android.content.Context,
        projectId: Int,
        templateTitle: String,
        templateDesc: String,
        templatePriority: Int,
        templateTags: String,
        subtaskList: List<String>
    ) {
        val serializedSubtasks = subtaskList.joinToString(";;") { "$it|false" }
        saveTask(
            context = context,
            title = templateTitle,
            desc = templateDesc,
            date = null,
            time = null,
            hasAlarm = false,
            ringtoneUri = null,
            repeatMode = null,
            priority = templatePriority,
            tags = templateTags,
            subTasks = serializedSubtasks,
            isProjectTask = true,
            projectId = projectId
        )
    }

    fun switchBook(bookId: Int) {
        _currentBookId.value = bookId
        com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.setActiveBook(bookId)
    }

    fun saveTask(
        context: android.content.Context,
        title: String,
        desc: String,
        date: Long?,
        time: Long?,
        hasAlarm: Boolean,
        ringtoneUri: String?,
        repeatMode: String?,
        repeatInterval: Int = 1,
        repeatUnit: String = "Days",
        repeatStarts: Long? = null,
        repeatEndsType: String = "Never",
        repeatEndsValue: Long? = null,
        repeatDaysOfWeek: String? = null,
        repeatMonths: String? = null,
        repeatYears: String? = null,
        isFavorite: Boolean = false,
        isImportant: Boolean = false,
        subTasks: String = "",
        tags: String = "",
        priority: Int = 4,
        isProjectTask: Boolean = false,
        projectId: Int? = null,
        status: String = "Pending",
        comments: String = "",
        attachments: String = "",
        assignee: String = "",
        existingTask: TaskEntity? = null
    ) {
        viewModelScope.launch {
            val targetBookId = if (isProjectTask && projectId != null) projectId else (existingTask?.bookId ?: _currentBookId.value)
            val syncId = existingTask?.syncId ?: ("task_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8))

            if (existingTask != null) {
                // Update
                val updated = existingTask.copy(
                    syncId = syncId,
                    title = title,
                    description = desc,
                    dueDate = date,
                    dueTime = time,
                    hasAlarm = hasAlarm,
                    ringtoneUri = ringtoneUri,
                    repeatMode = repeatMode,
                    repeatInterval = repeatInterval,
                    repeatUnit = repeatUnit,
                    repeatStarts = repeatStarts,
                    repeatEndsType = repeatEndsType,
                    repeatEndsValue = repeatEndsValue,
                    repeatDaysOfWeek = repeatDaysOfWeek,
                    repeatMonths = repeatMonths,
                    repeatYears = repeatYears,
                    isFavorite = isFavorite,
                    isImportant = isImportant || priority <= 2,
                    subTasks = subTasks,
                    tags = tags,
                    priority = priority,
                    isProjectTask = if (isProjectTask) true else existingTask.isProjectTask,
                    projectId = projectId ?: existingTask.projectId,
                    status = status,
                    comments = comments,
                    attachments = attachments,
                    assignee = assignee
                )
                repository.updateTask(updated)
                
                // Refresh alarms
                com.example.moderntodo.util.AlarmScheduler.cancel(context, updated.id ?: 0)
                if (hasAlarm || updated.dueTime != null || updated.dueDate != null) {
                    com.example.moderntodo.util.AlarmScheduler.schedule(context, updated)
                }
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    context,
                    repository,
                    existingTask.projectId ?: existingTask.bookId
                )
            } else {
                // Insert
                val task = TaskEntity(
                    syncId = syncId,
                    title = title,
                    description = desc,
                    dueDate = date,
                    dueTime = time,
                    bookId = targetBookId,
                    hasAlarm = hasAlarm,
                    ringtoneUri = ringtoneUri,
                    repeatMode = repeatMode,
                    repeatInterval = repeatInterval,
                    repeatUnit = repeatUnit,
                    repeatStarts = repeatStarts,
                    repeatEndsType = repeatEndsType,
                    repeatEndsValue = repeatEndsValue,
                    repeatDaysOfWeek = repeatDaysOfWeek,
                    repeatMonths = repeatMonths,
                    repeatYears = repeatYears,
                    isFavorite = isFavorite,
                    isImportant = isImportant || priority <= 2,
                    subTasks = subTasks,
                    tags = tags,
                    priority = priority,
                    isProjectTask = isProjectTask,
                    projectId = if (isProjectTask) (projectId ?: targetBookId) else null,
                    status = status,
                    comments = comments,
                    attachments = attachments,
                    assignee = assignee
                )
                val id = repository.insertTask(task)
                val insertedTask = task.copy(id = id.toInt())
                if (hasAlarm || insertedTask.dueTime != null || insertedTask.dueDate != null) {
                    com.example.moderntodo.util.AlarmScheduler.schedule(context, insertedTask)
                }
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    context,
                    repository,
                    targetBookId
                )
            }
        }
    }

    fun updateTaskComment(task: TaskEntity, newComment: String) {
        viewModelScope.launch {
            repository.updateTask(task.copy(comments = newComment))
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                task.projectId ?: task.bookId
            )
        }
    }

    fun updateTaskStatus(task: TaskEntity, newStatus: String) {
        viewModelScope.launch {
            val isCompleted = newStatus == "Full Complete"
            repository.updateTask(task.copy(status = newStatus, isCompleted = isCompleted))
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                task.projectId ?: task.bookId
            )
        }
    }

    fun toggleTaskRepeat(task: TaskEntity, mode: String? = null) {
        viewModelScope.launch {
            val isCurrentlyRepeating = !task.repeatMode.isNullOrBlank() && task.repeatMode != "None"
            val newMode = if (mode != null) {
                if (mode == "None" || mode == "Off") null else mode
            } else {
                if (isCurrentlyRepeating) null else "Daily"
            }
            val newUnit = when (newMode) {
                "Weekly" -> "Weeks"
                "Monthly" -> "Months"
                "Yearly" -> "Years"
                else -> "Days"
            }
            val updated = task.copy(
                repeatMode = newMode,
                repeatInterval = if (newMode != null && task.repeatInterval <= 0) 1 else task.repeatInterval,
                repeatUnit = if (newMode != null) newUnit else task.repeatUnit
            )
            repository.updateTask(updated)
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                task.projectId ?: task.bookId
            )
        }
    }

    fun toggleTaskCompletion(context: android.content.Context, task: TaskEntity) {
        viewModelScope.launch {
            val isNowCompleted = !task.isCompleted
            val updatedTask = task.copy(isCompleted = isNowCompleted)
            repository.updateTask(updatedTask)
            
            if (isNowCompleted) {
                com.example.moderntodo.util.AlarmScheduler.cancel(context, task.id ?: 0)
                
                // Handle repetition
                if (task.repeatMode != null && task.repeatMode != "None") {
                    val nextDate = task.dueDate?.let { advanceTime(it, task.repeatInterval, task.repeatUnit) }
                    val nextTime = task.dueTime?.let { advanceTime(it, task.repeatInterval, task.repeatUnit) }
                    
                    // Check Ends condition
                    var shouldRepeat = true
                    if (task.repeatEndsType == "OnDate" && task.repeatEndsValue != null && nextDate != null) {
                        if (nextDate > task.repeatEndsValue) {
                            shouldRepeat = false
                        }
                    } else if (task.repeatEndsType == "After" && task.repeatEndsValue != null) {
                        // Very naive approach: decrement ends value each time
                        if (task.repeatEndsValue <= 1) {
                            shouldRepeat = false
                        }
                    }

                    if (shouldRepeat) {
                        val newEndsValue = if (task.repeatEndsType == "After" && task.repeatEndsValue != null) task.repeatEndsValue - 1 else task.repeatEndsValue
                        val clonedTask = task.copy(
                            id = 0, // new ID for insertion
                            isCompleted = false,
                            dueDate = nextDate,
                            dueTime = nextTime,
                            repeatEndsValue = newEndsValue
                        )
                        val newId = repository.insertTask(clonedTask)
                        if (clonedTask.hasAlarm) {
                            com.example.moderntodo.util.AlarmScheduler.schedule(context, clonedTask.copy(id = newId.toInt()))
                        }
                    }
                }
            } else if (updatedTask.hasAlarm) {
                com.example.moderntodo.util.AlarmScheduler.schedule(context, updatedTask)
            }
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                context,
                repository,
                task.projectId ?: task.bookId
            )
        }
    }

    private fun advanceTime(timeMillis: Long, interval: Int, unit: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        when (unit) {
            "Days" -> cal.add(Calendar.DAY_OF_YEAR, interval)
            "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, interval)
            "Months" -> cal.add(Calendar.MONTH, interval)
            "Years" -> cal.add(Calendar.YEAR, interval)
        }
        return cal.timeInMillis
    }

    fun addBook(name: String, context: Context? = null, userEmail: String? = null) {
        viewModelScope.launch {
            val id = repository.insertTaskBook(TaskBookEntity(name = name, isProject = false))
            val bookId = id.toInt()
            if (context != null) {
                val code = com.example.moderntodo.data.remote.SharedBookService.generateInviteCode(name, bookId)
                com.example.moderntodo.data.remote.SharedBookService.saveInviteCode(context, bookId, code, userEmail)
                val ownerName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "Owner"
                com.example.moderntodo.data.remote.SharedBookService.exportBookPayload(
                    context = context,
                    repository = repository,
                    bookId = bookId,
                    ownerName = ownerName,
                    ownerEmail = userEmail
                )
            }
        }
    }

    fun updateBook(book: TaskBookEntity, newName: String) {
        viewModelScope.launch {
            repository.updateTaskBook(book.copy(name = newName))
            book.id?.let {
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    com.example.moderntodo.ModernToDoApp.instance,
                    repository,
                    it
                )
            }
        }
    }

    fun deleteBook(book: TaskBookEntity) {
        viewModelScope.launch {
            repository.deleteTaskBook(book)
            if (_currentBookId.value == book.id) {
                val allBooks = normalTaskBooks.value.ifEmpty { taskBooks.value }
                val nextBook = allBooks.firstOrNull { (it.id ?: 1) != book.id }
                if (nextBook != null) {
                    _currentBookId.value = nextBook.id ?: 1
                }
            }
        }
    }

    fun deleteTask(task: TaskEntity, context: Context? = null) {
        viewModelScope.launch {
            val ctx = context ?: com.example.moderntodo.ModernToDoApp.instance
            val targetBookId = task.projectId ?: task.bookId
            task.syncId?.let { sId ->
                com.example.moderntodo.data.remote.SharedBookService.recordDeletedTask(
                    ctx,
                    targetBookId,
                    sId,
                    repository.userEmail
                )
            }
            repository.deleteTask(task)
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                ctx,
                repository,
                targetBookId
            )
        }
    }

    fun getTasksForBook(bookId: Int): Flow<List<TaskEntity>> = repository.getTasksForBook(bookId)

    suspend fun getTasksForBookList(bookId: Int): List<TaskEntity> = repository.getTasksForBookList(bookId)

    fun duplicateBook(book: TaskBookEntity, onCreated: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val newId = repository.duplicateBook(book)
            onCreated(newId.toInt())
        }
    }

    fun completeAllTasksInBook(bookId: Int) {
        viewModelScope.launch {
            repository.completeAllTasksInBook(bookId)
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                bookId
            )
        }
    }

    fun deleteCompletedTasksInBook(bookId: Int) {
        viewModelScope.launch {
            repository.deleteCompletedTasksInBook(bookId)
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                bookId
            )
        }
    }

    fun addTaskToBook(
        bookId: Int,
        title: String,
        description: String = "",
        dueDate: Long? = null,
        isImportant: Boolean = false,
        priority: Int = 4,
        tags: String = "",
        isProjectTask: Boolean = true
    ) {
        viewModelScope.launch {
            val syncId = "task_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
            val task = TaskEntity(
                syncId = syncId,
                title = title,
                description = description,
                bookId = bookId,
                dueDate = dueDate,
                isImportant = isImportant || priority <= 2,
                priority = priority,
                tags = tags,
                isProjectTask = isProjectTask,
                projectId = if (isProjectTask) bookId else null
            )
            val id = repository.insertTask(task)
            val insertedTask = task.copy(id = id.toInt())
            if (insertedTask.dueDate != null || insertedTask.dueTime != null || insertedTask.hasAlarm) {
                com.example.moderntodo.util.AlarmScheduler.schedule(com.example.moderntodo.ModernToDoApp.instance, insertedTask)
            }
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                bookId
            )
        }
    }

    fun updateTaskPriority(task: TaskEntity, newPriority: Int) {
        viewModelScope.launch {
            val isImp = newPriority <= 2
            repository.updateTask(task.copy(priority = newPriority, isImportant = isImp))
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                com.example.moderntodo.ModernToDoApp.instance,
                repository,
                task.projectId ?: task.bookId
            )
        }
    }

    fun addBookWithDetails(
        name: String,
        description: String = "",
        colorHex: String = "#2563EB",
        iconName: String = "folder",
        deadline: Long? = null,
        category: String = "General",
        initialTaskTitle: String = "",
        context: Context? = null,
        userEmail: String? = null,
        onCreated: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.insertTaskBook(TaskBookEntity(name = name, isProject = true))
            val bookId = id.toInt()
            com.example.moderntodo.util.ProjectPreferences.setProjectMetadata(
                bookId = bookId,
                colorHex = colorHex,
                iconName = iconName,
                description = description,
                deadline = deadline,
                category = category
            )
            if (initialTaskTitle.isNotBlank()) {
                val syncId = "task_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
                repository.insertTask(
                    TaskEntity(
                        syncId = syncId,
                        title = initialTaskTitle,
                        bookId = bookId,
                        isProjectTask = true,
                        projectId = bookId
                    )
                )
            }
            if (context != null) {
                val code = com.example.moderntodo.data.remote.SharedBookService.generateInviteCode(name, bookId)
                com.example.moderntodo.data.remote.SharedBookService.saveInviteCode(context, bookId, code, userEmail)
                val ownerName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "Owner"
                com.example.moderntodo.data.remote.SharedBookService.exportBookPayload(
                    context = context,
                    repository = repository,
                    bookId = bookId,
                    ownerName = ownerName,
                    ownerEmail = userEmail
                )
            }
            onCreated(bookId)
        }
    }

    // -------------------------------------------------------------
    // Pro Batch Operations & Multi-Select
    // -------------------------------------------------------------
    fun deleteTasks(tasks: List<TaskEntity>, context: Context) {
        viewModelScope.launch {
            val bookIds = mutableSetOf<Int>()
            tasks.forEach { task ->
                val bId = task.projectId ?: task.bookId
                bookIds.add(bId)
                task.syncId?.let { sId ->
                    com.example.moderntodo.data.remote.SharedBookService.recordDeletedTask(
                        context,
                        bId,
                        sId,
                        repository.userEmail
                    )
                }
                AlarmScheduler.cancel(context, task.id ?: 0)
                repository.deleteTask(task)
            }
            bookIds.forEach { bId ->
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    context,
                    repository,
                    bId
                )
            }
        }
    }

    fun batchSetCompletion(context: Context, tasks: List<TaskEntity>, completed: Boolean) {
        viewModelScope.launch {
            val bookIds = mutableSetOf<Int>()
            tasks.forEach { task ->
                val bId = task.projectId ?: task.bookId
                bookIds.add(bId)
                val updated = task.copy(isCompleted = completed, status = if (completed) "Full Complete" else "Pending")
                repository.updateTask(updated)
                if (completed) {
                    AlarmScheduler.cancel(context, task.id ?: 0)
                } else if (updated.hasAlarm || updated.dueTime != null) {
                    AlarmScheduler.schedule(context, updated)
                }
            }
            bookIds.forEach { bId ->
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    context,
                    repository,
                    bId
                )
            }
        }
    }

    fun batchSetPriority(tasks: List<TaskEntity>, priority: Int) {
        viewModelScope.launch {
            val bookIds = mutableSetOf<Int>()
            tasks.forEach { task ->
                val bId = task.projectId ?: task.bookId
                bookIds.add(bId)
                repository.updateTask(task.copy(priority = priority, isImportant = priority <= 2))
            }
            bookIds.forEach { bId ->
                com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                    com.example.moderntodo.ModernToDoApp.instance,
                    repository,
                    bId
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Natural Language Smart Task Parser (e.g. "Meeting tomorrow 4pm p1 #work")
    // -------------------------------------------------------------
    fun parseAndCreateTask(context: Context, input: String, defaultBookId: Int = _currentBookId.value, isProject: Boolean = false, projectId: Int? = null) {
        if (input.isBlank()) return
        viewModelScope.launch {
            var title = input.trim()
            var priority = 4
            var date: Long? = null
            var time: Long? = null
            val tagsList = mutableListOf<String>()

            // Extract Priority: p1, p2, p3, p4
            val priorityRegex = Regex("""\b[pP]([1-4])\b""")
            priorityRegex.find(title)?.let { match ->
                priority = match.groupValues[1].toInt()
                title = title.replace(match.value, "").trim()
            }

            // Extract Tags: #tag
            val tagRegex = Regex("""#[a-zA-Z0-9_-]+""")
            tagRegex.findAll(title).forEach { match ->
                tagsList.add(match.value)
                title = title.replace(match.value, "").trim()
            }

            val cal = Calendar.getInstance()
            // Extract Dates: today, tomorrow
            if (title.contains(Regex("""\btoday\b""", RegexOption.IGNORE_CASE))) {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                date = cal.timeInMillis
                title = title.replace(Regex("""\btoday\b""", RegexOption.IGNORE_CASE), "").trim()
            } else if (title.contains(Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE))) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                date = cal.timeInMillis
                title = title.replace(Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE), "").trim()
            }

            // Extract Time: e.g. at 5pm, 10:30am, 4pm
            val timeRegex = Regex("""(?:at\s+)?\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|AM|PM)\b""")
            timeRegex.find(title)?.let { match ->
                var hour = match.groupValues[1].toInt()
                val minute = if (match.groupValues[2].isNotBlank()) match.groupValues[2].toInt() else 0
                val ampm = match.groupValues[3].uppercase()
                if (ampm == "PM" && hour < 12) hour += 12
                if (ampm == "AM" && hour == 12) hour = 0

                val tCal = Calendar.getInstance()
                if (date != null) tCal.timeInMillis = date
                tCal.set(Calendar.HOUR_OF_DAY, hour)
                tCal.set(Calendar.MINUTE, minute)
                tCal.set(Calendar.SECOND, 0)
                time = tCal.timeInMillis
                if (date == null) date = tCal.timeInMillis
                title = title.replace(match.value, "").trim()
            }

            title = title.replace(Regex("""\s+"""), " ").trim()
            if (title.isBlank()) title = input.trim()

            val targetBookId = if (isProject && projectId != null) projectId else defaultBookId
            val syncId = "task_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
            val newTask = TaskEntity(
                syncId = syncId,
                title = title,
                dueDate = date,
                dueTime = time,
                hasAlarm = time != null,
                priority = priority,
                tags = tagsList.joinToString(" "),
                bookId = targetBookId,
                isProjectTask = isProject,
                projectId = if (isProject) (projectId ?: defaultBookId) else null
            )
            val newId = repository.insertTask(newTask)
            if (newTask.hasAlarm || newTask.dueTime != null) {
                AlarmScheduler.schedule(context, newTask.copy(id = newId.toInt()))
            }
            com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.notifyLocalMutation(
                context,
                repository,
                targetBookId
            )
        }
    }
}

class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
