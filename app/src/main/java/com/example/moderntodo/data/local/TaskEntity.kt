package com.example.moderntodo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val dueTime: Long? = null,
    val bookId: Int = 1,
    val hasAlarm: Boolean = false,
    val ringtoneUri: String? = null,
    val repeatMode: String? = null,
    val repeatInterval: Int = 1,
    val repeatUnit: String = "Days",
    val repeatStarts: Long? = null,
    val repeatEndsType: String = "Never",
    val repeatEndsValue: Long? = null,
    val repeatDaysOfWeek: String? = null,
    val repeatMonths: String? = null,
    val repeatYears: String? = null,
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val subTasks: String = "",
    val tags: String = "",
    val status: String = "Pending",
    val comments: String = "",
    val priority: Int = 4, // 1: Urgent (Red), 2: High (Orange), 3: Medium (Blue), 4: Low (Green)
    val isProjectTask: Boolean = false,
    val projectId: Int? = null,
    val attachments: String = "",
    val assignee: String = "",
    val syncId: String? = null
)
