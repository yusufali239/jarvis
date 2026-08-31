package com.example.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.agent.JarvisState
import com.example.domain.agent.LiveSessionState
import com.example.domain.agent.SystemStatus
import com.example.presentation.components.ArcCoreVisualizer
import com.example.presentation.components.SystemStatusHud
import com.example.presentation.components.ThinkingTraceCard
import com.example.presentation.components.VoiceWaveform
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun HomeScreen(
    state: JarvisState,
    systemStatus: SystemStatus,
    activeEngineLabel: String = "AUTO",
    wakeWordState: com.example.core.audio.wakeword.WakeWordState = com.example.core.audio.wakeword.WakeWordState.ACTIVE,
    liveState: LiveSessionState,
    liveStreamingAssistantText: String,
    livePartialTranscript: String,
    liveAudioAmplitude: Float,
    audioAmplitude: Float,
    isListening: Boolean,
    partialVoiceText: String,
    lastAssistantResponse: String,
    thinkingTrace: String?,
    onStartLive: () -> Unit,
    onEndLive: () -> Unit,
    onInterruptLive: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onInterrupt: () -> Unit,
    onSendCommand: (String, Boolean) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var useHighThinking by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val isLiveActive = liveState != LiveSessionState.DISCONNECTED

    val quickCommands = listOf(
        "🎙 Включи режим диалога" to false,
        "Открой YouTube" to false,
        "Что на экране?" to false,
        "Включи фонарик" to false,
        "Который час?" to false,
        "Прочитай уведомления" to false,
        "Высокое мышление: спланируй день" to true
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Telemetry Status HUD
        SystemStatusHud(
            status = systemStatus,
            activeEngineLabel = activeEngineLabel,
            wakeWordState = wakeWordState
        )

        // 2. Arc Core Visualizer with state pill
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            val effectiveAmp = if (isLiveActive) liveAudioAmplitude else audioAmplitude
            ArcCoreVisualizer(
                state = if (isLiveActive) {
                    when (liveState) {
                        LiveSessionState.LISTENING -> JarvisState.LISTENING
                        LiveSessionState.THINKING -> JarvisState.THINKING
                        LiveSessionState.SPEAKING -> JarvisState.SPEAKING
                        LiveSessionState.EXECUTING -> JarvisState.EXECUTING
                        LiveSessionState.ERROR -> JarvisState.ERROR
                        else -> JarvisState.IDLE
                    }
                } else state,
                audioAmplitude = effectiveAmp,
                canvasSize = 200.dp
            )
        }

        // 3. State Status Badge
        if (isLiveActive) {
            LiveSessionBadge(liveState = liveState)
        } else {
            StateStatusPill(state = state)
        }

        // 4. Voice Waveform & Partial Voice Input
        val showWaveform = isListening || state == JarvisState.SPEAKING || (isLiveActive && liveState != LiveSessionState.DISCONNECTED)
        if (showWaveform) {
            VoiceWaveform(
                isListening = isListening || liveState == LiveSessionState.LISTENING,
                amplitude = if (isLiveActive) liveAudioAmplitude else audioAmplitude,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        val displayPartial = if (isLiveActive && livePartialTranscript.isNotBlank()) {
            livePartialTranscript
        } else if (partialVoiceText.isNotBlank() && isListening) {
            partialVoiceText
        } else ""

        if (displayPartial.isNotBlank()) {
            Text(
                text = "“$displayPartial”",
                color = JarvisCyanGlow,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // 5. Assistant Output Message Box (Standard or Live Real-time Streaming)
        val activeAssistantText = if (isLiveActive && liveStreamingAssistantText.isNotBlank()) {
            liveStreamingAssistantText
        } else {
            lastAssistantResponse
        }

        if (activeAssistantText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurface)
                    .border(
                        1.dp,
                        if (isLiveActive) JarvisEmerald.copy(alpha = 0.5f) else JarvisCyan.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLiveActive) "J.A.R.V.I.S. LIVE STREAM" else "J.A.R.V.I.S. RESPONSE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isLiveActive) JarvisEmerald else JarvisCyan
                        )
                        if (isLiveActive) {
                            Text(
                                text = "REALTIME BIDI AUDIO",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = JarvisEmerald
                            )
                        } else if (state != JarvisState.IDLE) {
                            Text(
                                text = systemStatus.activeTaskDescription.take(30),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = JarvisTextMuted
                            )
                        }
                    }

                    Text(
                        text = activeAssistantText,
                        fontSize = 14.sp,
                        color = JarvisTextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // 6. Neural Thinking Trace (Gemini 3.1 Pro)
        if (thinkingTrace != null && thinkingTrace.isNotBlank() && !isLiveActive) {
            ThinkingTraceCard(thinkingText = thinkingTrace)
        }

        // 7. Large Primary Action Controls: LIVE CONVERSATION & Push-to-Talk
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // PROMINENT "LIVE CONVERSATION" HERO BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(56.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = if (isLiveActive) JarvisEmerald else JarvisCyan)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (isLiveActive) {
                            Brush.horizontalGradient(listOf(JarvisRed, Color(0xFFDC2626)))
                        } else {
                            Brush.horizontalGradient(listOf(JarvisCyan, JarvisElectricBlue))
                        }
                    )
                    .clickable {
                        if (isLiveActive) onEndLive() else onStartLive()
                    }
                    .testTag("live_conversation_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isLiveActive) Icons.Default.PhoneInTalk else Icons.Default.Mic,
                        contentDescription = "Live Mode",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isLiveActive) "⏹ END LIVE CONVERSATION" else "🎙 LIVE CONVERSATION (REAL-TIME)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Secondary Quick Actions (Interrupt / Single Command)
            if (isLiveActive && liveState == LiveSessionState.SPEAKING) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisRed.copy(alpha = 0.2f))
                        .border(1.dp, JarvisRed, RoundedCornerShape(16.dp))
                        .clickable { onInterruptLive() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("live_interrupt_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interrupt",
                            tint = JarvisRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "INTERRUPT / BARGE-IN",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = JarvisRed
                        )
                    }
                }
            } else if (!isLiveActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state != JarvisState.IDLE) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(JarvisRed.copy(alpha = 0.2f))
                                .border(1.dp, JarvisRed, CircleShape)
                                .clickable { onInterrupt() }
                                .testTag("interrupt_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Action",
                                tint = JarvisRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // Push to talk single turn button
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(JarvisSurfaceVariant)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                            .clickable {
                                if (isListening) onStopListening() else onStartListening()
                            }
                            .padding(horizontal = 18.dp)
                            .testTag("voice_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = JarvisCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isListening) "STOP" else "SINGLE COMMAND",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyan
                            )
                        }
                    }
                }
            }
        }

        // 8. Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((cmd, isHigh) in quickCommands) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isHigh) JarvisAmber.copy(alpha = 0.15f) else JarvisSurfaceVariant)
                        .border(
                            1.dp,
                            if (isHigh) JarvisAmber.copy(alpha = 0.6f) else JarvisCyan.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            if (cmd.contains("диалога")) {
                                onStartLive()
                            } else {
                                onSendCommand(cmd, isHigh)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = cmd,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isHigh) JarvisGold else JarvisTextPrimary
                    )
                }
            }
        }

        // 9. Text Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { useHighThinking = !useHighThinking },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Toggle High Thinking",
                    tint = if (useHighThinking) JarvisAmber else JarvisTextMuted
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                placeholder = {
                    Text(
                        text = if (useHighThinking) "Command (High Thinking)..." else "Instruct J.A.R.V.I.S...",
                        color = JarvisTextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val cmd = textInput
                        textInput = ""
                        onSendCommand(cmd, useHighThinking)
                    }
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier
                    .size(36.dp)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isNotBlank()) JarvisCyan else JarvisTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun LiveSessionBadge(liveState: LiveSessionState) {
    val (bgColor, textColor, borderColor) = when (liveState) {
        LiveSessionState.DISCONNECTED -> Triple(JarvisSurfaceVariant, JarvisTextMuted, JarvisBorder)
        LiveSessionState.CONNECTING, LiveSessionState.RECONNECTING -> Triple(JarvisAmber.copy(alpha = 0.2f), JarvisGold, JarvisAmber)
        LiveSessionState.CONNECTED -> Triple(JarvisCyan.copy(alpha = 0.2f), JarvisCyanGlow, JarvisCyan)
        LiveSessionState.LISTENING -> Triple(JarvisEmerald.copy(alpha = 0.25f), JarvisEmerald, JarvisEmerald)
        LiveSessionState.THINKING -> Triple(JarvisAmber.copy(alpha = 0.25f), JarvisGold, JarvisAmber)
        LiveSessionState.SPEAKING -> Triple(JarvisCyan.copy(alpha = 0.3f), JarvisCyanGlow, JarvisCyan)
        LiveSessionState.EXECUTING -> Triple(JarvisElectricBlue.copy(alpha = 0.25f), JarvisCyan, JarvisCyan)
        LiveSessionState.INTERRUPTED -> Triple(JarvisRed.copy(alpha = 0.2f), JarvisRed, JarvisRed)
        LiveSessionState.ERROR -> Triple(JarvisRed.copy(alpha = 0.25f), JarvisRed, JarvisRed)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = "LIVE: ${liveState.label} — ${liveState.description}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun StateStatusPill(state: JarvisState) {
    val (bgColor, textColor, borderColor) = when (state) {
        JarvisState.IDLE -> Triple(JarvisSurfaceVariant, JarvisCyan, JarvisCyan.copy(alpha = 0.4f))
        JarvisState.LISTENING -> Triple(JarvisCyan.copy(alpha = 0.2f), JarvisCyanGlow, JarvisCyanGlow)
        JarvisState.THINKING, JarvisState.PLANNING -> Triple(JarvisAmber.copy(alpha = 0.2f), JarvisGold, JarvisAmber)
        JarvisState.EXECUTING -> Triple(JarvisElectricBlue.copy(alpha = 0.25f), JarvisCyan, JarvisCyan)
        JarvisState.VERIFYING -> Triple(JarvisSurfaceVariant, JarvisCyanGlow, JarvisCyanGlow)
        JarvisState.SPEAKING -> Triple(JarvisCyan.copy(alpha = 0.25f), JarvisCyanGlow, JarvisCyan)
        JarvisState.ERROR -> Triple(JarvisRed.copy(alpha = 0.2f), JarvisRed, JarvisRed)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${state.label} — ${state.description}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.8.sp
        )
    }
}
