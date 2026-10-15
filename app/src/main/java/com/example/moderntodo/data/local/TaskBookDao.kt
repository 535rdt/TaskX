package com.example.moderntodo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskBookDao {
    @Query("SELECT * FROM task_books ORDER BY id ASC")
    fun getAllTaskBooks(): Flow<List<TaskBookEntity>>

    @Query("SELECT * FROM task_books WHERE isProject = 0 ORDER BY id ASC")
    fun getNormalTaskBooks(): Flow<List<TaskBookEntity>>

    @Query("SELECT * FROM task_books WHERE isProject = 1 ORDER BY id ASC")
    fun getProjectTaskBooks(): Flow<List<TaskBookEntity>>

    @Query("UPDATE task_books SET isProject = 1 WHERE id = :id")
    suspend fun markAsProject(id: Int): Int

    @Query("SELECT * FROM task_books ORDER BY id ASC")
    suspend fun getAllTaskBooksList(): List<TaskBookEntity>

    @Query("SELECT * FROM task_books WHERE id = :id LIMIT 1")
    suspend fun getTaskBookById(id: Int): TaskBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskBook(book: TaskBookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskBooks(books: List<TaskBookEntity>): List<Long>

    @Update
    suspend fun updateTaskBook(book: TaskBookEntity): Int

    @Delete
    suspend fun deleteTaskBook(book: TaskBookEntity): Int

    @Query("DELETE FROM task_books WHERE id = :id")
    suspend fun deleteTaskBookById(id: Int): Int
}
