package com.example.moderntodo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_books")
data class TaskBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val name: String,
    val isProject: Boolean = false
)
