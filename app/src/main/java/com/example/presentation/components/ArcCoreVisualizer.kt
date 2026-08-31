package com.example.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.agent.JarvisState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisRed

@Composable
fun ArcCoreVisualizer(
    state: JarvisState,
    audioAmplitude: Float,
    modifier: Modifier = Modifier,
    canvasSize: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == JarvisState.THINKING || state == JarvisState.PLANNING) 3000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == JarvisState.EXECUTING) 2000 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == JarvisState.LISTENING || state == JarvisState.SPEAKING) 600 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val (primaryGlowColor, accentColor) = when (state) {
        JarvisState.IDLE -> Pair(JarvisCyan, JarvisElectricBlue)
        JarvisState.LISTENING -> Pair(JarvisCyanGlow, JarvisCyan)
        JarvisState.THINKING -> Pair(JarvisGold, JarvisAmber)
        JarvisState.PLANNING -> Pair(JarvisAmber, JarvisCyan)
        JarvisState.EXECUTING -> Pair(JarvisCyan, JarvisElectricBlue)
        JarvisState.VERIFYING -> Pair(JarvisCyanGlow, JarvisGold)
        JarvisState.SPEAKING -> Pair(JarvisCyanGlow, JarvisCyan)
        JarvisState.ERROR -> Pair(JarvisRed, JarvisAmber)
    }

    val dynamicPulse = if (state == JarvisState.LISTENING) {
        pulseScale + (audioAmplitude * 0.35f)
    } else if (state == JarvisState.SPEAKING) {
        pulseScale + 0.12f
    } else {
        pulseScale
    }

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) * 0.9f

            // 1. Center Glowing Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 0.45f * dynamicPulse
                ),
                radius = radius * 0.45f * dynamicPulse,
                center = center
            )

            // Inner Core Solid Ring
            drawCircle(
                color = primaryGlowColor,
                radius = radius * 0.22f * dynamicPulse,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. Middle Rotating Segmented Ring
            rotate(outerRotation, pivot = center) {
                drawSegmentedRing(
                    center = center,
                    radius = radius * 0.65f,
                    segments = 6,
                    gapDegrees = 18f,
                    color = primaryGlowColor.copy(alpha = 0.85f),
                    strokeWidth = 3.5.dp.toPx()
                )
            }

            // 3. Counter-rotating HUD Tickers Ring
            rotate(innerRotation, pivot = center) {
                drawSegmentedRing(
                    center = center,
                    radius = radius * 0.82f,
                    segments = 12,
                    gapDegrees = 10f,
                    color = accentColor.copy(alpha = 0.7f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 4. Outer Fine Tech Border Ring
            drawCircle(
                color = primaryGlowColor.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f)
                )
            )

            // 4 Corner Cardinal Indicators
            val markerLength = 10.dp.toPx()
            drawLine(
                color = primaryGlowColor,
                start = Offset(center.x, center.y - radius - markerLength),
                end = Offset(center.x, center.y - radius + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = primaryGlowColor,
                start = Offset(center.x, center.y + radius - 4.dp.toPx()),
                end = Offset(center.x, center.y + radius + markerLength),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = primaryGlowColor,
                start = Offset(center.x - radius - markerLength, center.y),
                end = Offset(center.x - radius + 4.dp.toPx(), center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = primaryGlowColor,
                start = Offset(center.x + radius - 4.dp.toPx(), center.y),
                end = Offset(center.x + radius + markerLength, center.y),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawSegmentedRing(
    center: Offset,
    radius: Float,
    segments: Int,
    gapDegrees: Float,
    color: Color,
    strokeWidth: Float
) {
    val totalDegrees = 360f
    val segmentSweep = (totalDegrees - (segments * gapDegrees)) / segments

    for (i in 0 until segments) {
        val startAngle = i * (segmentSweep + gapDegrees)
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
