package com.example.moderntodo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_notes")
data class ProjectNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val projectId: Int,
    val title: String,
    val content: String = "",
    val attachedTaskIds: String = "", // Comma-separated task IDs, e.g. "1,4,7"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
