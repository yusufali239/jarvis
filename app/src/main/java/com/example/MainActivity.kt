package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.MainViewModel
import com.example.presentation.camera.VisionScannerScreen
import com.example.presentation.components.RiskConfirmationDialog
import com.example.presentation.conversation.ConversationScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.logs.ActivityLogScreen
import com.example.presentation.memory.MemoryScreen
import com.example.presentation.permissions.PermissionCenterScreen
import com.example.presentation.settings.SettingsScreen
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.MyApplicationTheme

enum class JarvisNavTab(val label: String, val icon: ImageVector) {
    CORE("CORE", Icons.Default.RadioButtonChecked),
    CHAT("CHAT", Icons.Default.Chat),
    VISION("VISION", Icons.Default.Visibility),
    LOGS("LOGS", Icons.Default.Terminal),
    MEMORY("MEMORY", Icons.Default.Bookmark),
    ACCESS("ACCESS", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                JarvisApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(JarvisNavTab.CORE) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val jarvisState by viewModel.jarvisState.collectAsStateWithLifecycle()
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val lastAssistantResponse by viewModel.lastAssistantResponse.collectAsStateWithLifecycle()
    val thinkingTrace by viewModel.thinkingTrace.collectAsStateWithLifecycle()
    val isConversationMode by viewModel.isConversationMode.collectAsStateWithLifecycle()

    // Multi-AI & Wake Word States
    val activeProviderType by viewModel.activeProviderType.collectAsStateWithLifecycle()
    val selectedModels by viewModel.selectedModels.collectAsStateWithLifecycle()
    val isAutoFallbackEnabled by viewModel.isAutoFallbackEnabled.collectAsStateWithLifecycle()
    val lastUsedEngine by viewModel.lastUsedEngine.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val wakeWordState by viewModel.wakeWordState.collectAsStateWithLifecycle()
    val isWakeWordEnabled by viewModel.isWakeWordEnabled.collectAsStateWithLifecycle()

    val liveState by viewModel.liveSessionState.collectAsStateWithLifecycle()
    val liveStreamingAssistantText by viewModel.liveStreamingAssistantText.collectAsStateWithLifecycle()
    val livePartialTranscript by viewModel.livePartialTranscript.collectAsStateWithLifecycle()
    val liveAudioAmplitude by viewModel.liveAudioAmplitude.collectAsStateWithLifecycle()

    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val partialVoiceText by viewModel.partialVoiceText.collectAsStateWithLifecycle()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val permissionItems by viewModel.permissionItems.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Modal Authorization Dialog for Medium/High Risk Actions
    pendingConfirmation?.let { pending ->
        RiskConfirmationDialog(
            pending = pending,
            scope = coroutineScope
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "J.A.R.V.I.S.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = JarvisCyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = " OS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyanGlow,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSettingsOpen = !isSettingsOpen },
                        modifier = Modifier.testTag("settings_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isSettingsOpen) JarvisCyan else JarvisTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisSurface,
                    titleContentColor = JarvisTextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = JarvisSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(0.5.dp, JarvisBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                JarvisNavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab && !isSettingsOpen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                            isSettingsOpen = false
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan,
                            selectedTextColor = JarvisCyan,
                            indicatorColor = JarvisSurfaceVariant,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = if (isSettingsOpen) "SETTINGS" else selectedTab.name, label = "tab_transition") { tabName ->
                when (tabName) {
                    "SETTINGS" -> {
                        SettingsScreen(
                            config = config,
                            activeProviderType = activeProviderType,
                            selectedModels = selectedModels,
                            isAutoFallbackEnabled = isAutoFallbackEnabled,
                            availableModels = availableModels,
                            wakeWordState = wakeWordState,
                            isWakeWordEnabled = isWakeWordEnabled,
                            isConversationMode = isConversationMode,
                            onSelectProvider = { viewModel.setAIProvider(it) },
                            onSelectModel = { prov, modelId -> viewModel.setSelectedModel(prov, modelId) },
                            onToggleAutoFallback = { viewModel.setAutoFallback(it) },
                            onToggleWakeWord = { viewModel.setWakeWordEnabled(it) },
                            onSaveGroqApiKey = { viewModel.saveGroqApiKey(it) },
                            onSaveOpenRouterApiKey = { viewModel.saveOpenRouterApiKey(it) },
                            onSaveGeminiApiKey = { viewModel.saveGeminiApiKey(it) },
                            onUpdateConfig = { viewModel.updateConfig(it) },
                            onToggleConversationMode = { viewModel.toggleConversationMode(it) },
                            onPurgeAllData = { viewModel.purgeAllData() }
                        )
                    }
                    JarvisNavTab.CORE.name -> {
                        HomeScreen(
                            state = jarvisState,
                            systemStatus = systemStatus,
                            activeEngineLabel = lastUsedEngine.take(24),
                            wakeWordState = wakeWordState,
                            liveState = liveState,
                            liveStreamingAssistantText = liveStreamingAssistantText,
                            livePartialTranscript = livePartialTranscript,
                            liveAudioAmplitude = liveAudioAmplitude,
                            audioAmplitude = audioAmplitude,
                            isListening = isListening,
                            partialVoiceText = partialVoiceText,
                            lastAssistantResponse = lastAssistantResponse,
                            thinkingTrace = thinkingTrace,
                            onStartLive = { viewModel.startLiveSession() },
                            onEndLive = { viewModel.endLiveSession() },
                            onInterruptLive = { viewModel.interruptLiveSpeech() },
                            onStartListening = { viewModel.startListening() },
                            onStopListening = { viewModel.stopListening() },
                            onInterrupt = { viewModel.interrupt() },
                            onSendCommand = { cmd, isHigh -> viewModel.sendCommand(cmd, isHigh) }
                        )
                    }
                    JarvisNavTab.CHAT.name -> {
                        ConversationScreen(
                            conversations = conversations,
                            onSendMessage = { msg, isHigh -> viewModel.sendCommand(msg, isHigh) },
                            onSpeakText = { txt -> viewModel.speakText(txt) },
                            onClearHistory = { viewModel.clearConversations() }
                        )
                    }
                    JarvisNavTab.VISION.name -> {
                        VisionScannerScreen(
                            onAnalyzeImage = { bmp, prompt -> viewModel.analyzeImage(bmp, prompt) }
                        )
                    }
                    JarvisNavTab.LOGS.name -> {
                        ActivityLogScreen(
                            actionLogs = actionLogs,
                            onClearLogs = { viewModel.clearActionLogs() }
                        )
                    }
                    JarvisNavTab.MEMORY.name -> {
                        MemoryScreen(
                            memories = memories,
                            onAddMemory = { k, v -> viewModel.addMemory(k, v) },
                            onDeleteMemory = { id -> viewModel.deleteMemory(id) },
                            onClearAllMemories = { viewModel.clearAllMemories() }
                        )
                    }
                    JarvisNavTab.ACCESS.name -> {
                        PermissionCenterScreen(
                            permissionItems = permissionItems,
                            onRequestPermission = { perm -> permissionLauncher.launch(arrayOf(perm)) },
                            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                            onOpenNotificationListenerSettings = { viewModel.openNotificationListenerSettings() },
                            onOpenAppSettings = { viewModel.openAppSettings() }
                        )
                    }
                }
            }
        }
    }
}
