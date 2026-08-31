package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.wakeword.WakeWordState
import com.example.domain.agent.SystemStatus
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@Composable
fun SystemStatusHud(
    status: SystemStatus,
    activeEngineLabel: String = "AUTO",
    wakeWordState: WakeWordState = WakeWordState.ACTIVE,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JarvisSurface.copy(alpha = 0.85f))
            .border(1.dp, JarvisCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SYSTEM TELEMETRY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisCyan,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "• AI: $activeEngineLabel",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(isActive = true, activeColor = JarvisEmerald)
                Text(
                    text = "ONLINE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = JarvisEmerald
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudBadge(label = "AI NEURAL", isActive = status.isAiOnline)
            HudBadge(label = "VOICE ENGINE", isActive = status.isVoiceOnline)
            HudBadge(
                label = "WAKE WORD",
                isActive = wakeWordState == WakeWordState.ACTIVE || wakeWordState == WakeWordState.DETECTED,
                activeColor = if (wakeWordState == WakeWordState.DETECTED) JarvisEmerald else JarvisCyan
            )
            HudBadge(label = "TOOLS (16)", isActive = status.isToolsOnline)
            HudBadge(label = "MEMORY", isActive = status.isMemoryOnline)
        }
    }
}

@Composable
private fun HudBadge(
    label: String,
    isActive: Boolean,
    activeColor: Color = JarvisCyan
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatusDot(
            isActive = isActive,
            activeColor = activeColor,
            inactiveColor = JarvisAmber
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isActive) JarvisTextPrimary else JarvisTextMuted
        )
    }
}

@Composable
private fun StatusDot(
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color = JarvisAmber
) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (isActive) activeColor else inactiveColor)
    )
}
