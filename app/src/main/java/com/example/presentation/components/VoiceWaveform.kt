package com.example.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisElectricBlue

@Composable
fun VoiceWaveform(
    isListening: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 18
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")

    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animProgress by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (i * 45), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val heightMultiplier = if (isListening) {
                (animProgress * 0.5f + (amplitude * 0.9f)).coerceIn(0.15f, 1.0f)
            } else {
                0.12f
            }

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(fraction = heightMultiplier)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(JarvisCyanGlow, JarvisCyan, JarvisElectricBlue)
                        )
                    )
            )
        }
    }
}
