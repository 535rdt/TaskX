package com.example.moderntodo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE bookId = :bookId AND isCompleted = 0 ORDER BY id DESC")
    fun getPendingTasks(bookId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE bookId = :bookId AND isCompleted = 0 AND isProjectTask = 0 ORDER BY id DESC")
    fun getNormalPendingTasks(bookId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY id DESC")
    fun getCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDate ASC, id DESC")
    fun getAllPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND isProjectTask = 0 ORDER BY dueDate ASC, id DESC")
    fun getAllNormalPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isProjectTask = 1 ORDER BY id DESC")
    fun getAllProjectTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isProjectTask = 1 AND isCompleted = 0 ORDER BY id DESC")
    fun getAllProjectPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isProjectTask = 1 AND (projectId = :projectId OR bookId = :projectId) ORDER BY id DESC")
    fun getProjectTasks(projectId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isProjectTask = 1 AND (projectId = :projectId OR bookId = :projectId) AND isCompleted = 0 ORDER BY id DESC")
    fun getProjectPendingTasks(projectId: Int): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isProjectTask = 1, projectId = :bookId WHERE bookId = :bookId")
    suspend fun setTasksAsProject(bookId: Int): Int

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun updateTask(task: TaskEntity): Int

    @Delete
    suspend fun deleteTask(task: TaskEntity): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int): Int

    @Query("DELETE FROM tasks WHERE bookId = :bookId")
    suspend fun deleteTasksByBookId(bookId: Int): Int

    @Query("SELECT * FROM tasks WHERE bookId = :bookId ORDER BY id DESC")
    fun getTasksForBook(bookId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE bookId = :bookId ORDER BY id DESC")
    suspend fun getTasksForBookList(bookId: Int): List<TaskEntity>

    @Query("UPDATE tasks SET isCompleted = 1, status = 'Full Complete' WHERE bookId = :bookId")
    suspend fun completeAllTasksInBook(bookId: Int): Int

    @Query("SELECT * FROM tasks WHERE syncId = :syncId LIMIT 1")
    suspend fun getTaskBySyncId(syncId: String): TaskEntity?

    @Query("DELETE FROM tasks WHERE syncId = :syncId")
    suspend fun deleteTaskBySyncId(syncId: String): Int

    @Query("DELETE FROM tasks WHERE syncId IN (:syncIds)")
    suspend fun deleteTasksBySyncIds(syncIds: List<String>): Int

    @Query("DELETE FROM tasks WHERE bookId = :bookId AND isCompleted = 1")
    suspend fun deleteCompletedTasksInBook(bookId: Int): Int
}
