package com.example.moderntodo.util

import androidx.compose.ui.graphics.Color
import com.example.moderntodo.data.local.TaskEntity

enum class PriorityLevel(
    val level: Int,
    val label: String,
    val code: String,
    val color: Color,
    val hexColor: String,
    val description: String
) {
    URGENT(1, "Urgent", "P1", Color(0xFFEF4444), "#EF4444", "Urgent • Critical work"),     // Red
    HIGH(2, "High", "P2", Color(0xFFF97316), "#F97316", "High • Important work"),             // Orange
    MEDIUM(3, "Medium", "P3", Color(0xFF3B82F6), "#3B82F6", "Medium • Standard work"),       // Blue
    LOW(4, "Low", "P4", Color(0xFF10B981), "#10B981", "Low • Routine work");               // Green

    companion object {
        fun fromLevel(level: Int): PriorityLevel {
            return when (level) {
                1 -> URGENT
                2 -> HIGH
                3 -> MEDIUM
                4 -> LOW
                else -> LOW
            }
        }

        fun fromTask(task: TaskEntity): PriorityLevel {
            if (task.priority in 1..4) {
                return fromLevel(task.priority)
            }
            if (task.isImportant) {
                return URGENT
            }
            val tagsLower = task.tags.lowercase()
            return when {
                tagsLower.contains("p1") || tagsLower.contains("urgent") -> URGENT
                tagsLower.contains("p2") || tagsLower.contains("high") -> HIGH
                tagsLower.contains("p3") || tagsLower.contains("medium") -> MEDIUM
                tagsLower.contains("p4") || tagsLower.contains("low") -> LOW
                else -> LOW
            }
        }
    }
}
