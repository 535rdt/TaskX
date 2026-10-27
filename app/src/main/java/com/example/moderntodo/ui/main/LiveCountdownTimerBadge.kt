package com.example.moderntodo.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moderntodo.data.local.TaskEntity
import java.util.Calendar

/**
 * Calculates the exact deadline timestamp for a given task:
 * - If [task.dueTime] is present, uses it directly.
 * - If only [task.dueDate] is present, computes 23:59:59.999 of that date.
 */
fun getTaskEffectiveDeadline(task: TaskEntity): Long? {
    if (task.dueTime != null) return task.dueTime
    if (task.dueDate != null) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = task.dueDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
    return null
}

/**
 * Renders a live, 1-second updating countdown timer badge showing
 * exact hours, minutes, and seconds remaining until task deadline,
 * or the live overdue duration if the deadline has passed.
 */
@Composable
fun LiveCountdownTimerBadge(
    task: TaskEntity,
    modifier: Modifier = Modifier
) {
    if (task.isCompleted) return
    val deadlineMillis = getTaskEffectiveDeadline(task) ?: return

    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val diff = deadlineMillis - currentTime
    val isOverdue = diff < 0
    val absDiff = kotlin.math.abs(diff)

    val totalSecs = absDiff / 1000
    val days = totalSecs / 86400
    val hours = (totalSecs % 86400) / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60

    val timeStr = if (days > 0) {
        "${days}d ${"%02d".format(hours)}h ${"%02d".format(mins)}m ${"%02d".format(secs)}s"
    } else {
        "${"%02d".format(hours)}h ${"%02d".format(mins)}m ${"%02d".format(secs)}s"
    }

    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector
    val labelText: String

    when {
        isOverdue -> {
            containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)
            contentColor = Color(0xFFEF4444)
            icon = Icons.Filled.WarningAmber
            labelText = "Overdue: $timeStr"
        }
        diff < 60 * 60 * 1000L -> {
            // Less than 1 hour remaining: urgency amber
            containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)
            contentColor = Color(0xFFD97706)
            icon = Icons.Outlined.HourglassBottom
            labelText = "$timeStr left"
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            contentColor = MaterialTheme.colorScheme.primary
            icon = Icons.Outlined.Timer
            labelText = "$timeStr left"
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, contentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}
