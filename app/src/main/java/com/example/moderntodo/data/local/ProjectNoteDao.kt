package com.example.moderntodo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectNoteDao {
    @Query("SELECT * FROM project_notes WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getNotesForProject(projectId: Int): Flow<List<ProjectNoteEntity>>

    @Query("SELECT * FROM project_notes WHERE projectId = :projectId ORDER BY updatedAt DESC")
    suspend fun getNotesForProjectList(projectId: Int): List<ProjectNoteEntity>

    @Query("SELECT * FROM project_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<ProjectNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ProjectNoteEntity): Long

    @Update
    suspend fun updateNote(note: ProjectNoteEntity): Int

    @Delete
    suspend fun deleteNote(note: ProjectNoteEntity): Int

    @Query("DELETE FROM project_notes WHERE projectId = :projectId")
    suspend fun deleteNotesForProject(projectId: Int): Int
}
