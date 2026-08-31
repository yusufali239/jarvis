package com.example.presentation.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.memory.ActionLogEntity
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogScreen(
    actionLogs: List<ActionLogEntity>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "ACTION AUDIT LOGS (${actionLogs.size})",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            IconButton(onClick = onClearLogs) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Logs",
                    tint = JarvisTextMuted
                )
            }
        }

        if (actionLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No action execution logs recorded yet.",
                    color = JarvisTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(actionLogs) { log ->
                    ActionLogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun ActionLogCard(log: ActionLogEntity) {
    val timeFormatted = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    val (statusColor, statusBg) = when (log.status) {
        "SUCCESS" -> Pair(JarvisEmerald, JarvisEmerald.copy(alpha = 0.15f))
        "FAILED" -> Pair(JarvisRed, JarvisRed.copy(alpha = 0.15f))
        "EXECUTING" -> Pair(JarvisCyan, JarvisCyan.copy(alpha = 0.15f))
        "CANCELLED" -> Pair(JarvisAmber, JarvisAmber.copy(alpha = 0.15f))
        else -> Pair(JarvisTextSecondary, JarvisSurfaceVariant)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = log.actionType,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )

            Text(
                text = log.status,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = log.description,
            fontSize = 12.sp,
            color = JarvisTextPrimary,
            fontFamily = FontFamily.Monospace
        )

        if (!log.details.isNullOrBlank() && log.details != log.description) {
            Text(
                text = log.details,
                fontSize = 11.sp,
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Risk: ${log.riskLevel}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (log.riskLevel == "HIGH" || log.riskLevel == "CRITICAL") JarvisRed else JarvisTextMuted
            )
            Text(
                text = timeFormatted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = JarvisTextMuted
            )
        }
    }
}
