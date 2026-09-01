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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import com.example.android.system.SamsungDeviceSpecs
import com.example.core.ai.GeminiConfig
import com.example.core.ai.TtsDiagnosticState
import com.example.core.ai.TtsEnginePreference
import com.example.core.ai.TtsLanguagePreference
import com.example.core.ai.TtsLanguageSupportStatus
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
    ttsDiagnosticState: TtsDiagnosticState = TtsDiagnosticState(),
    samsungSpecs: SamsungDeviceSpecs? = null,
    wakeWordState: WakeWordState,
    isWakeWordEnabled: Boolean,
    isConversationMode: Boolean,
    diagnosticReport: com.example.core.network.FullDiagnosticsReport? = null,
    isRunningDiagnostics: Boolean = false,
    onRunDiagnostics: () -> Unit = {},
    onSelectTtsEngine: (TtsEnginePreference) -> Unit = {},
    onSelectTtsLanguage: (TtsLanguagePreference) -> Unit = {},
    onSelectTtsVoice: (String?) -> Unit = {},
    onTestRussianVoice: () -> Unit = {},
    onOpenTtsSettings: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
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
    val cloudVoices = listOf("Kore", "Aoede", "Puck", "Fenrir", "Charon")
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
                text = "SYSTEM SETTINGS & DEVICE OPTIMIZATION",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
        }

        // ==========================================
        // 0. PHYSICAL DEVICE & SAMSUNG S21 ULTRA PROFILER
        // ==========================================
        samsungSpecs?.let { specs ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurface)
                    .border(
                        1.dp,
                        if (specs.isS21Ultra) JarvisCyanGlow else JarvisBorder,
                        RoundedCornerShape(12.dp)
                    )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (specs.isS21Ultra) JarvisCyan else JarvisTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (specs.isS21Ultra) "SAMSUNG GALAXY S21 ULTRA (ACTIVE)" else "HARDWARE PROFILER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (specs.isS21Ultra) JarvisCyan else JarvisTextPrimary
                        )
                    }

                    Text(
                        text = if (specs.isS21Ultra) "OPTIMIZED" else "DETECTED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(JarvisEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpecRow(label = "DEVICE MODEL", value = "${specs.manufacturer} ${specs.model}")
                    SpecRow(label = "OS PLATFORM", value = specs.androidVersion)
                    SpecRow(label = "MEMORY (RAM)", value = "${specs.availableRamMb} MB Free / ${specs.totalRamMb} MB Total")
                    SpecRow(label = "AUDIO ROUTING", value = specs.activeAudioOutput)
                    SpecRow(label = "DISPLAY PROFILE", value = specs.displaySummary)
                    SpecRow(
                        label = "ONE UI BATTERY RESTRICTION",
                        value = if (specs.isBatteryOptimizationIgnored) "UNRESTRICTED (PERFECT)" else "RESTRICTED (APP MAY SLEEP)",
                        valueColor = if (specs.isBatteryOptimizationIgnored) JarvisEmerald else JarvisAmber
                    )
                }

                if (!specs.isBatteryOptimizationIgnored) {
                    Button(
                        onClick = onRequestBatteryOptimization,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisAmber.copy(alpha = 0.2f),
                            contentColor = JarvisAmber
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Exempt from Samsung Battery Saver (Wake Word Protection)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==========================================
        // 1. RUSSIAN TTS ENGINE & VOICE DIAGNOSTICS (PHYSICAL DEVICE FIX)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(
                    1.dp,
                    if (ttsDiagnosticState.isRussianSupported) JarvisBorder else JarvisAmber.copy(alpha = 0.6f),
                    RoundedCornerShape(12.dp)
                )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "RUSSIAN TTS ENGINE DIAGNOSTICS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
                }

                Text(
                    text = ttsDiagnosticState.statusSummary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ttsDiagnosticState.isRussianSupported) JarvisEmerald else JarvisAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (ttsDiagnosticState.isRussianSupported) JarvisEmerald.copy(alpha = 0.15f) else JarvisAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Diagnostic Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SpecRow(label = "ACTIVE TTS ENGINE", value = ttsDiagnosticState.activeEngineName)
                SpecRow(label = "PACKAGE", value = ttsDiagnosticState.activeEnginePackage.ifBlank { "system default" })
                SpecRow(
                    label = "RUSSIAN VOICE SUPPORT",
                    value = ttsDiagnosticState.russianSupportStatus.label,
                    valueColor = if (ttsDiagnosticState.isRussianSupported) JarvisEmerald else JarvisAmber
                )
                SpecRow(label = "ACTIVE VOICE", value = ttsDiagnosticState.activeVoiceName)
                SpecRow(label = "TARGET LOCALE", value = ttsDiagnosticState.activeLocale)
            }

            if (!ttsDiagnosticState.isRussianSupported) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisAmber.copy(alpha = 0.15f))
                        .border(1.dp, JarvisAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisAmber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Russian Voice Data Missing in Current Engine",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisAmber
                            )
                        }
                        Text(
                            text = ttsDiagnosticState.diagnosticMessage,
                            fontSize = 11.sp,
                            color = JarvisTextPrimary
                        )
                        Button(
                            onClick = onOpenTtsSettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open TTS Settings / Install Russian Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Engine Selector
            Text(
                text = "SELECT SYNTHESIS ENGINE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    TtsEnginePreference.AUTO_BEST,
                    TtsEnginePreference.GOOGLE_TTS,
                    TtsEnginePreference.SAMSUNG_TTS
                ).forEach { pref ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisSurfaceVariant)
                            .border(1.dp, JarvisBorder, RoundedCornerShape(6.dp))
                            .clickable { onSelectTtsEngine(pref) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pref.label.split(" ").first(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                }
            }

            // Language Mode Selector
            Text(
                text = "LANGUAGE ROUTING MODE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    TtsLanguagePreference.AUTO_DETECT,
                    TtsLanguagePreference.FORCE_RUSSIAN,
                    TtsLanguagePreference.FORCE_ENGLISH
                ).forEach { langPref ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisSurfaceVariant)
                            .border(1.dp, JarvisBorder, RoundedCornerShape(6.dp))
                            .clickable { onSelectTtsLanguage(langPref) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (langPref) {
                                TtsLanguagePreference.AUTO_DETECT -> "Auto (RU/EN/UZ)"
                                TtsLanguagePreference.FORCE_RUSSIAN -> "Force RU"
                                TtsLanguagePreference.FORCE_ENGLISH -> "Force EN"
                                else -> "Custom"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                }
            }

            // Test Russian Voice Action Button
            Button(
                onClick = onTestRussianVoice,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Тест русской озвучки J.A.R.V.I.S.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==========================================
        // 2. CONNECTION & MULTI-AI DIAGNOSTICS TEST
        // ==========================================
        com.example.presentation.components.ConnectionDiagnosticsCard(
            report = diagnosticReport,
            isRunning = isRunningDiagnostics,
            onRunDiagnostics = onRunDiagnostics
        )

        // ==========================================
        // 3. "HEY JARVIS" WAKE WORD SYSTEM
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "VOICE WAKE WORD ENGINE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS: ${wakeWordState.name}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when (wakeWordState) {
                        WakeWordState.ACTIVE -> JarvisEmerald
                        WakeWordState.DETECTED -> JarvisCyan
                        WakeWordState.PAUSED -> JarvisAmber
                        WakeWordState.DISABLED -> JarvisTextMuted
                    },
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "TRIGGERS: \"Джарвис\", \"Hey Jarvis\", \"Слушай\"",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = JarvisTextMuted
                )
            }
        }

        // ==========================================
        // 4. MULTI-AI PROVIDER & ROUTING
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
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
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PRIMARY AI PROVIDER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
                }

                Text(
                    text = "ACTIVE: ${activeProviderType.displayName}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyanGlow
                )
            }

            // Provider Selector Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AIProviderType.entries) { provider ->
                    val isSelected = activeProviderType == provider
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) JarvisCyan else JarvisSurfaceVariant)
                            .clickable { onSelectProvider(provider) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = provider.displayName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else JarvisTextPrimary
                        )
                    }
                }
            }

            // Auto-Fallback Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Route, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Auto Fallback Routing",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary
                        )
                    }
                    Text(
                        text = "Auto switches between providers if rate limited (HTTP 429) or offline.",
                        fontSize = 10.sp,
                        color = JarvisTextSecondary
                    )
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

            // Active Provider Model Selector
            if (activeProviderType != AIProviderType.AUTO) {
                val modelsForProvider = availableModels[activeProviderType] ?: emptyList()
                val currentSelectedModel = selectedModels[activeProviderType] ?: ""

                Text(
                    text = "SELECT MODEL FOR ${activeProviderType.displayName}:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextSecondary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (modelInfo in modelsForProvider) {
                        val isChosen = currentSelectedModel == modelInfo.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChosen) JarvisCyan.copy(alpha = 0.2f) else JarvisSurfaceVariant)
                                .border(1.dp, if (isChosen) JarvisCyan else JarvisBorder, RoundedCornerShape(6.dp))
                                .clickable { onSelectModel(activeProviderType, modelInfo.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(
                                    text = modelInfo.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChosen) JarvisCyan else JarvisTextPrimary
                                )
                                Text(
                                    text = modelInfo.contextWindow,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = JarvisTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. SECURE API KEYS CONFIGURATION
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
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
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "SECURE API KEYS (LOCAL STORE)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            ApiKeyStatusRow(
                title = "Gemini API Key (Server / Local)",
                isConfigured = SecretProvider.isApiKeyConfigured,
                onEdit = {
                    tempKeyInput = ""
                    apiKeyDialogProvider = AIProviderType.GEMINI
                }
            )

            ApiKeyStatusRow(
                title = "Groq API Key (Ultra-Fast)",
                isConfigured = SecretProvider.isGroqConfigured,
                onEdit = {
                    tempKeyInput = ""
                    apiKeyDialogProvider = AIProviderType.GROQ
                }
            )

            ApiKeyStatusRow(
                title = "OpenRouter API Key (Multi-LLM)",
                isConfigured = SecretProvider.isOpenRouterConfigured,
                onEdit = {
                    tempKeyInput = ""
                    apiKeyDialogProvider = AIProviderType.OPENROUTER
                }
            )

            ApiKeyStatusRow(
                title = "xAI / Grok API Key",
                isConfigured = SecretProvider.isXaiConfigured,
                onEdit = {
                    tempKeyInput = ""
                    apiKeyDialogProvider = AIProviderType.GROK
                }
            )
        }

        // ==========================================
        // 6. CLOUD TTS VOICE PERSONA
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
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
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "GEMINI CLOUD VOICE PRESET",
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
                for (voice in cloudVoices) {
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
        // 7. CONTINUOUS CONVERSATION MODE
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
        // 8. DATA PURGE & RESET
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
private fun SpecRow(
    label: String,
    value: String,
    valueColor: Color = JarvisTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = JarvisTextMuted
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
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
