package com.example.moderntodo.repository

import android.util.Log
import com.example.moderntodo.data.local.TaskBookDao
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskDao
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.data.remote.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import com.example.moderntodo.data.local.ProjectNoteDao
import com.example.moderntodo.data.local.ProjectNoteEntity

class TaskRepository(
    private val taskDao: TaskDao,
    private val taskBookDao: TaskBookDao,
    private val projectNoteDao: ProjectNoteDao,
    val userEmail: String? = null
) {
    private val syncScope = CoroutineScope(Dispatchers.IO)

    fun getNotesForProject(projectId: Int): Flow<List<ProjectNoteEntity>> =
        projectNoteDao.getNotesForProject(projectId)

    suspend fun getNotesForProjectList(projectId: Int): List<ProjectNoteEntity> =
        projectNoteDao.getNotesForProjectList(projectId)

    suspend fun allTaskBooksList(): List<TaskBookEntity> =
        taskBookDao.getAllTaskBooksList()

    fun getAllNotes(): Flow<List<ProjectNoteEntity>> =
        projectNoteDao.getAllNotes()

    suspend fun insertNote(note: ProjectNoteEntity): Long =
        projectNoteDao.insertNote(note)

    suspend fun updateNote(note: ProjectNoteEntity) =
        projectNoteDao.updateNote(note)

    suspend fun deleteNote(note: ProjectNoteEntity) =
        projectNoteDao.deleteNote(note)

    val allTaskBooks: Flow<List<TaskBookEntity>> = taskBookDao.getAllTaskBooks()
    val normalTaskBooks: Flow<List<TaskBookEntity>> = taskBookDao.getNormalTaskBooks()
    val projectTaskBooks: Flow<List<TaskBookEntity>> = taskBookDao.getProjectTaskBooks()

    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()
    val allPendingTasks: Flow<List<TaskEntity>> = taskDao.getAllPendingTasks()
    val allNormalPendingTasks: Flow<List<TaskEntity>> = taskDao.getAllNormalPendingTasks()
    val allProjectTasks: Flow<List<TaskEntity>> = taskDao.getAllProjectTasks()
    val allProjectPendingTasks: Flow<List<TaskEntity>> = taskDao.getAllProjectPendingTasks()
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getPendingTasks(bookId: Int): Flow<List<TaskEntity>> {
        return taskDao.getPendingTasks(bookId)
    }

    fun getNormalPendingTasks(bookId: Int): Flow<List<TaskEntity>> {
        return taskDao.getNormalPendingTasks(bookId)
    }

    fun getProjectTasks(projectId: Int): Flow<List<TaskEntity>> {
        return taskDao.getProjectTasks(projectId)
    }

    fun getProjectPendingTasks(projectId: Int): Flow<List<TaskEntity>> {
        return taskDao.getProjectPendingTasks(projectId)
    }

    suspend fun ensureInitialized() {
        try {
            val existing = taskBookDao.getAllTaskBooksList()
            if (existing.isEmpty()) {
                taskBookDao.insertTaskBook(TaskBookEntity(id = 1, name = "My Tasks", isProject = false))
            } else {
                for (book in existing) {
                    val bId = book.id ?: 1
                    if (bId != 1 && com.example.moderntodo.util.ProjectPreferences.isProject(bId)) {
                        if (!book.isProject) {
                            taskBookDao.markAsProject(bId)
                        }
                        taskDao.setTasksAsProject(bId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TaskRepository", "ensureInitialized error", e)
        }
        syncWithRemote()
    }

    suspend fun insertTask(task: TaskEntity): Long {
        val cleanTask = if (task.id == 0) task.copy(id = null) else task
        val generatedId = taskDao.insertTask(cleanTask)
        val insertedTask = cleanTask.copy(id = generatedId.toInt())

        syncScope.launch {
            try {
                SupabaseClient.api.insertTask(insertedTask)
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote insertTask skipped/failed: ${e.message}")
            }
        }
        return generatedId
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
        syncScope.launch {
            try {
                task.id?.let { SupabaseClient.api.updateTask("eq.$it", task) }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote updateTask skipped/failed: ${e.message}")
            }
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
        syncScope.launch {
            try {
                task.id?.let { SupabaseClient.api.deleteTask("eq.$it") }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote deleteTask skipped/failed: ${e.message}")
            }
        }
    }

    suspend fun getTaskBySyncId(syncId: String): TaskEntity? {
        return taskDao.getTaskBySyncId(syncId)
    }

    suspend fun deleteTaskBySyncId(syncId: String) {
        taskDao.deleteTaskBySyncId(syncId)
    }

    suspend fun deleteTasksBySyncIds(syncIds: List<String>) {
        if (syncIds.isNotEmpty()) {
            taskDao.deleteTasksBySyncIds(syncIds)
        }
    }

    suspend fun insertTaskBook(book: TaskBookEntity): Long {
        val cleanBook = if (book.id == 0) book.copy(id = null) else book
        val generatedId = taskBookDao.insertTaskBook(cleanBook)
        val insertedBook = cleanBook.copy(id = generatedId.toInt())

        syncScope.launch {
            try {
                SupabaseClient.api.insertTaskBook(insertedBook)
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote insertTaskBook skipped/failed: ${e.message}")
            }
        }
        return generatedId
    }

    suspend fun updateTaskBook(book: TaskBookEntity) {
        taskBookDao.updateTaskBook(book)
        syncScope.launch {
            try {
                book.id?.let { SupabaseClient.api.updateTaskBook("eq.$it", book) }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote updateTaskBook skipped/failed: ${e.message}")
            }
        }
    }

    suspend fun deleteTaskBook(book: TaskBookEntity) {
        book.id?.let { bookId ->
            taskDao.deleteTasksByBookId(bookId)
        }
        taskBookDao.deleteTaskBook(book)
        syncScope.launch {
            try {
                book.id?.let { SupabaseClient.api.deleteTaskBook("eq.$it") }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Remote deleteTaskBook skipped/failed: ${e.message}")
            }
        }
    }

    fun getTasksForBook(bookId: Int): Flow<List<TaskEntity>> {
        return taskDao.getTasksForBook(bookId)
    }

    suspend fun getTasksForBookList(bookId: Int): List<TaskEntity> {
        return taskDao.getTasksForBookList(bookId)
    }

    suspend fun duplicateBook(book: TaskBookEntity): Long {
        val newBook = TaskBookEntity(name = "${book.name} (Copy)")
        val newBookId = insertTaskBook(newBook)
        book.id?.let { oldId ->
            val tasks = taskDao.getTasksForBookList(oldId)
            val clonedTasks = tasks.map { it.copy(id = null, bookId = newBookId.toInt()) }
            if (clonedTasks.isNotEmpty()) {
                taskDao.insertTasks(clonedTasks)
            }
        }
        return newBookId
    }

    suspend fun completeAllTasksInBook(bookId: Int) {
        taskDao.completeAllTasksInBook(bookId)
    }

    suspend fun deleteCompletedTasksInBook(bookId: Int) {
        taskDao.deleteCompletedTasksInBook(bookId)
    }

    suspend fun getAllTasksForExport(): List<TaskEntity> {
        return try {
            taskDao.getAllTasksList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun importTasks(tasks: List<TaskEntity>) {
        try {
            taskDao.insertTasks(tasks)
            syncScope.launch {
                tasks.forEach { task ->
                    try {
                        SupabaseClient.api.insertTask(task.copy(id = null))
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncWithRemote() {
        syncScope.launch {
            try {
                val remoteBooks = SupabaseClient.api.getTaskBooks()
                if (remoteBooks.isNotEmpty()) {
                    taskBookDao.insertTaskBooks(remoteBooks)
                }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Sync task books skipped/failed: ${e.message}")
            }

            try {
                val remoteTasks = SupabaseClient.api.getAllTasks()
                if (remoteTasks.isNotEmpty()) {
                    taskDao.insertTasks(remoteTasks)
                }
            } catch (e: Exception) {
                Log.d("TaskRepository", "Sync tasks skipped/failed: ${e.message}")
            }
        }
    }
}
