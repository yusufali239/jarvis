package com.example.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ai.GeminiConfig
import com.example.core.ai.provider.AICapability
import com.example.core.ai.provider.AIModelInfo
import com.example.core.ai.provider.AIProviderType
import com.example.core.audio.wakeword.WakeWordState
import com.example.core.security.SecretProvider
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: GeminiConfig,
    activeProviderType: AIProviderType,
    selectedModels: Map<AIProviderType, String>,
    isAutoFallbackEnabled: Boolean,
    availableModels: Map<AIProviderType, List<AIModelInfo>>,
    wakeWordState: WakeWordState,
    isWakeWordEnabled: Boolean,
    isConversationMode: Boolean,
    onSelectProvider: (AIProviderType) -> Unit,
    onSelectModel: (AIProviderType, String) -> Unit,
    onToggleAutoFallback: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onSaveGroqApiKey: (String) -> Unit,
    onSaveOpenRouterApiKey: (String) -> Unit,
    onSaveGrokApiKey: (String) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onUpdateConfig: (GeminiConfig) -> Unit,
    onToggleConversationMode: (Boolean) -> Unit,
    onPurgeAllData: () -> Unit
) {
    val voices = listOf("Kore", "Aoede", "Puck", "Fenrir", "Charon")
    var apiKeyDialogProvider by remember { mutableStateOf<AIProviderType?>(null) }
    var tempKeyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "NEURAL CORE & MULTI-AI SETTINGS",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
        }

        // ==========================================
        // 1. "HEY JARVIS" WAKE WORD SYSTEM
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "\"HEY JARVIS\" WAKE WORD",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan
                        )
                        Text(
                            text = "Always-on local phrase trigger",
                            fontSize = 10.sp,
                            color = JarvisTextSecondary
                        )
                    }
                }

                Switch(
                    checked = isWakeWordEnabled,
                    onCheckedChange = onToggleWakeWord,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisCyan,
                        checkedTrackColor = JarvisCyanGlow.copy(alpha = 0.3f)
                    )
                )
            }

            // Status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(JarvisSurfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DETECTOR STATUS:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = JarvisTextMuted
                )

                val stateColor = when (wakeWordState) {
                    WakeWordState.ACTIVE -> JarvisEmerald
                    WakeWordState.DETECTED -> JarvisCyan
                    WakeWordState.PAUSED -> JarvisAmber
                    WakeWordState.DISABLED -> JarvisTextMuted
                }

                Text(
                    text = wakeWordState.label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = stateColor
                )
            }

            Text(
                text = "Supported wake phrases: \"Hey JARVIS\", \"Jarvis\", \"Хей Джарвис\", \"Джарвис\".\nOperates locally on device without sending background audio to external servers.",
                fontSize = 10.sp,
                color = JarvisTextSecondary,
                lineHeight = 14.sp
            )
        }

        // ==========================================
        // 2. MULTI-AI ENGINE SELECTOR
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AI ENGINE & MULTI-MODEL ROUTER",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            // Engine Selector Tabs
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(AIProviderType.values().toList()) { provider ->
                    val isSelected = activeProviderType == provider
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) JarvisCyan else JarvisSurfaceVariant)
                            .border(1.dp, if (isSelected) JarvisCyan else JarvisBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectProvider(provider) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.displayName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else JarvisTextPrimary
                        )
                    }
                }
            }

            // AUTO Fallback Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Auto Failover Protection",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                        Text(
                            text = "Failover: Gemini → Groq → OpenRouter → Grok on 429/503/timeout",
                            fontSize = 10.sp,
                            color = JarvisTextSecondary
                        )
                    }
                }

                Switch(
                    checked = isAutoFallbackEnabled,
                    onCheckedChange = onToggleAutoFallback,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisCyan,
                        checkedTrackColor = JarvisCyanGlow.copy(alpha = 0.3f)
                    )
                )
            }

            // Model Selection for Active Engine
            val currentEngineForModels = if (activeProviderType == AIProviderType.AUTO) AIProviderType.GEMINI else activeProviderType
            val models = availableModels[currentEngineForModels] ?: emptyList()
            val currentSelectedModelId = selectedModels[currentEngineForModels] ?: models.firstOrNull()?.id ?: ""

            Text(
                text = "AVAILABLE MODELS (${currentEngineForModels.displayName}):",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = JarvisTextMuted
            )

            for (model in models) {
                val isModelSelected = model.id == currentSelectedModelId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isModelSelected) JarvisSurfaceVariant else Color.Transparent)
                        .border(
                            1.dp,
                            if (isModelSelected) JarvisCyan else JarvisBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectModel(currentEngineForModels, model.id) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                text = model.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary
                            )
                            if (model.isRecommended) {
                                Text(
                                    text = "RECOMMENDED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisEmerald,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(JarvisEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (isModelSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "ID: ${model.id} • Context: ${model.contextWindow}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextMuted
                    )

                    if (model.description.isNotBlank()) {
                        Text(
                            text = model.description,
                            fontSize = 10.sp,
                            color = JarvisTextSecondary
                        )
                    }

                    // Capability Badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (cap in model.capabilities) {
                            val badgeColor = when (cap) {
                                AICapability.REALTIME_AUDIO -> JarvisCyan
                                AICapability.TOOL_CALLING -> JarvisEmerald
                                AICapability.VISION -> JarvisAmber
                                AICapability.STREAMING, AICapability.TEXT -> JarvisTextSecondary
                            }
                            Text(
                                text = cap.label.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(badgeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. SECURE API KEYS & CREDENTIALS
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "API CREDENTIALS & SECRETS VAULT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            // Gemini Key
            ApiKeyStatusRow(
                title = "Google Gemini API Key",
                isConfigured = SecretProvider.isGeminiConfigured,
                onEdit = {
                    apiKeyDialogProvider = AIProviderType.GEMINI
                    tempKeyInput = ""
                }
            )

            // Groq Key
            ApiKeyStatusRow(
                title = "Groq API Key",
                isConfigured = SecretProvider.isGroqConfigured,
                onEdit = {
                    apiKeyDialogProvider = AIProviderType.GROQ
                    tempKeyInput = ""
                }
            )

            // OpenRouter Key
            ApiKeyStatusRow(
                title = "OpenRouter API Key",
                isConfigured = SecretProvider.isOpenRouterConfigured,
                onEdit = {
                    apiKeyDialogProvider = AIProviderType.OPENROUTER
                    tempKeyInput = ""
                }
            )

            // Grok / xAI Key
            ApiKeyStatusRow(
                title = "xAI Grok API Key",
                isConfigured = SecretProvider.isXaiConfigured,
                onEdit = {
                    apiKeyDialogProvider = AIProviderType.GROK
                    tempKeyInput = ""
                }
            )
        }

        // ==========================================
        // 4. TTS & SPEECH SYNTHESIS
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "TTS VOICE PERSONA",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (voice in voices) {
                    val isChosen = config.ttsVoiceName == voice
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isChosen) JarvisCyan else JarvisSurfaceVariant)
                            .clickable { onUpdateConfig(config.copy(ttsVoiceName = voice)) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = voice,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isChosen) Color.Black else JarvisTextPrimary
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. CONTINUOUS CONVERSATION MODE
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = JarvisCyan)
                Column {
                    Text(
                        text = "Continuous Voice Dialogue",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )
                    Text(
                        text = "Auto-listens after J.A.R.V.I.S. finishes speaking.",
                        fontSize = 11.sp,
                        color = JarvisTextSecondary
                    )
                }
            }

            Switch(
                checked = isConversationMode,
                onCheckedChange = onToggleConversationMode,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisCyan,
                    checkedTrackColor = JarvisCyanGlow.copy(alpha = 0.3f)
                )
            )
        }

        // ==========================================
        // 6. DATA PURGE & RESET
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DATA PURGE & RESET",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisRed
            )
            Text(
                text = "Erase all conversation transcripts, long-term memory facts, and action audit logs stored in the local Room database.",
                fontSize = 11.sp,
                color = JarvisTextSecondary
            )

            Button(
                onClick = onPurgeAllData,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisRed, contentColor = Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Purge All Local J.A.R.V.I.S. Data",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Dialog for entering API Key
    apiKeyDialogProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { apiKeyDialogProvider = null },
            title = {
                Text(
                    text = "Set ${provider.displayName} API Key",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your custom ${provider.displayName} API key. Keys are securely stored locally.",
                        fontSize = 12.sp,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = tempKeyInput,
                        onValueChange = { tempKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (provider) {
                            AIProviderType.GEMINI -> onSaveGeminiApiKey(tempKeyInput)
                            AIProviderType.GROQ -> onSaveGroqApiKey(tempKeyInput)
                            AIProviderType.OPENROUTER -> onSaveOpenRouterApiKey(tempKeyInput)
                            AIProviderType.GROK -> onSaveGrokApiKey(tempKeyInput)
                            AIProviderType.AUTO -> {}
                        }
                        apiKeyDialogProvider = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                ) {
                    Text("Save Key", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { apiKeyDialogProvider = null }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ApiKeyStatusRow(
    title: String,
    isConfigured: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
            .clickable { onEdit() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = JarvisTextPrimary
            )
            Text(
                text = if (isConfigured) "Configured & Active" else "Not set (Tap to configure)",
                fontSize = 10.sp,
                color = if (isConfigured) JarvisEmerald else JarvisAmber
            )
        }

        Text(
            text = if (isConfigured) "READY" else "SET KEY",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConfigured) JarvisEmerald else JarvisAmber,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isConfigured) JarvisEmerald.copy(alpha = 0.15f) else JarvisAmber.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
