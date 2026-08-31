package com.example.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.JarvisApplication
import com.example.core.ai.GeminiConfig
import com.example.core.ai.provider.AIModelInfo
import com.example.core.ai.provider.AIProviderType
import com.example.core.audio.wakeword.WakeWordState
import com.example.core.permissions.PermissionItem
import com.example.core.security.SecretProvider
import com.example.data.memory.ActionLogEntity
import com.example.data.memory.ConversationEntity
import com.example.data.memory.MemoryEntity
import com.example.domain.agent.JarvisState
import com.example.domain.agent.LiveSessionState
import com.example.domain.agent.PendingConfirmation
import com.example.domain.agent.SystemStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JarvisApplication

    val jarvisState: StateFlow<JarvisState> = app.jarvisCore.currentState
    val systemStatus: StateFlow<SystemStatus> = app.jarvisCore.systemStatus
    val pendingConfirmation: StateFlow<PendingConfirmation?> = app.jarvisCore.pendingConfirmation
    val lastAssistantResponse: StateFlow<String> = app.jarvisCore.lastAssistantResponse
    val thinkingTrace: StateFlow<String?> = app.jarvisCore.latestThinkingTrace
    val isConversationMode: StateFlow<Boolean> = app.jarvisCore.isConversationMode

    // Multi-AI Routing States
    val activeProviderType: StateFlow<AIProviderType> = app.aiModelRouter.activeProviderType
    val selectedModels: StateFlow<Map<AIProviderType, String>> = app.aiModelRouter.selectedModels
    val isAutoFallbackEnabled: StateFlow<Boolean> = app.aiModelRouter.isAutoFallbackEnabled
    val lastUsedEngine: StateFlow<String> = app.aiModelRouter.lastUsedEngine

    private val _availableModels = MutableStateFlow<Map<AIProviderType, List<AIModelInfo>>>(emptyMap())
    val availableModels: StateFlow<Map<AIProviderType, List<AIModelInfo>>> = _availableModels.asStateFlow()

    // Wake Word States
    val wakeWordState: StateFlow<WakeWordState> = app.wakeWordDetector.state
    val isWakeWordEnabled: StateFlow<Boolean> = app.wakeWordDetector.isEnabled
    val detectedWakePhrase: StateFlow<String?> = app.wakeWordDetector.detectedPhrase

    // Live Continuous Voice Conversation States
    val liveSessionState: StateFlow<LiveSessionState> = app.liveConversationManager.sessionState
    val liveStreamingAssistantText: StateFlow<String> = app.liveConversationManager.liveStreamingAssistantText
    val livePartialTranscript: StateFlow<String> = app.liveConversationManager.livePartialTranscript
    val liveAudioAmplitude: StateFlow<Float> = app.liveConversationManager.liveAudioAmplitude
    val liveActiveTopic: StateFlow<String> = app.liveConversationManager.activeTopic

    val audioAmplitude: StateFlow<Float> = app.voiceRecognizer.rmsAmplitude
    val isListening: StateFlow<Boolean> = app.voiceRecognizer.isListening
    val partialVoiceText: StateFlow<String> = app.voiceRecognizer.partialResult

    // Chat History & Sessions
    val chatSessions: StateFlow<List<com.example.data.memory.ChatSessionEntity>> = app.memoryManager.allChatSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow(app.memoryManager.currentActiveSessionId)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _searchChatQuery = MutableStateFlow("")
    val searchChatQuery: StateFlow<String> = _searchChatQuery.asStateFlow()

    val filteredChatSessions: StateFlow<List<com.example.data.memory.ChatSessionEntity>> =
        kotlinx.coroutines.flow.combine(app.memoryManager.allChatSessions, _searchChatQuery) { list, q ->
            if (q.isBlank()) list else list.filter { it.title.contains(q, ignoreCase = true) || it.provider.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSessionMessages: StateFlow<List<com.example.data.memory.ChatMessageEntity>> =
        _activeSessionId.kotlinx.coroutines.flow.flatMapLatest { sId ->
            app.memoryManager.getMessagesForSession(sId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = app.memoryManager.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = app.memoryManager.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = app.memoryManager.allActionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionItems = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissionItems: StateFlow<List<PermissionItem>> = _permissionItems.asStateFlow()

    private val _config = MutableStateFlow(app.geminiClient.getConfig())
    val config: StateFlow<GeminiConfig> = _config.asStateFlow()

    init {
        refreshPermissions()
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            _availableModels.value = app.aiModelRouter.getAllAvailableModels()
        }
    }

    // AI Routing Controls
    fun setAIProvider(providerType: AIProviderType) {
        app.aiModelRouter.setProvider(providerType)
    }

    fun setSelectedModel(providerType: AIProviderType, modelId: String) {
        app.aiModelRouter.setSelectedModel(providerType, modelId)
    }

    fun setAutoFallback(enabled: Boolean) {
        app.aiModelRouter.setAutoFallback(enabled)
    }

    fun saveGroqApiKey(key: String) {
        SecretProvider.setGroqApiKey(key)
    }

    fun saveOpenRouterApiKey(key: String) {
        SecretProvider.setOpenRouterApiKey(key)
    }

    fun saveGrokApiKey(key: String) {
        SecretProvider.setXaiApiKey(key)
    }

    fun saveGeminiApiKey(key: String) {
        SecretProvider.setGeminiApiKey(key)
    }

    // Chat History Management
    fun createNewChat() {
        val prov = activeProviderType.value.displayName
        val model = selectedModels.value[activeProviderType.value] ?: "default"
        val newId = app.memoryManager.startNewSession(provider = prov, model = model)
        _activeSessionId.value = newId
    }

    fun selectChatSession(sessionId: String) {
        app.memoryManager.setActiveSessionId(sessionId)
        _activeSessionId.value = sessionId
    }

    fun renameChatSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            app.memoryManager.renameChatSession(sessionId, newTitle)
        }
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            app.memoryManager.deleteChatSession(sessionId)
            _activeSessionId.value = app.memoryManager.currentActiveSessionId
        }
    }

    fun setSearchChatQuery(query: String) {
        _searchChatQuery.value = query
    }

    // Wake Word Controls
    fun setWakeWordEnabled(enabled: Boolean) {
        app.wakeWordDetector.setEnabled(enabled)
    }

    // Live Conversation Controls
    fun startLiveSession() {
        app.liveConversationManager.startLiveSession()
    }

    fun endLiveSession() {
        app.liveConversationManager.endLiveSession()
    }

    fun interruptLiveSpeech() {
        app.liveConversationManager.interruptCurrentSpeech()
    }

    fun refreshPermissions() {
        _permissionItems.value = app.permissionManager.getAllPermissionsStatus()
        app.jarvisCore.refreshSystemStatus()
    }

    fun startListening() {
        app.jarvisCore.startListening()
    }

    fun stopListening() {
        app.jarvisCore.stopListening()
    }

    fun interrupt() {
        app.jarvisCore.interrupt()
    }

    fun sendCommand(command: String, forceHighThinking: Boolean = false) {
        app.jarvisCore.processUserCommand(
            userInput = command,
            forceHighThinking = forceHighThinking
        )
    }

    fun analyzeImage(bitmap: Bitmap, prompt: String) {
        app.jarvisCore.processUserCommand(
            userInput = prompt,
            image = bitmap,
            forceHighThinking = false
        )
    }

    fun speakText(text: String) {
        app.ttsManager.speak(text)
    }

    fun toggleConversationMode(enabled: Boolean) {
        app.jarvisCore.toggleConversationMode(enabled)
    }

    fun updateConfig(newConfig: GeminiConfig) {
        _config.value = newConfig
        app.geminiClient.updateConfig(newConfig)
    }

    fun addMemory(key: String, value: String) {
        viewModelScope.launch {
            app.memoryManager.remember(key, value)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            app.memoryManager.deleteById(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            app.memoryManager.clearAllMemory()
        }
    }

    fun clearConversations() {
        viewModelScope.launch {
            app.memoryManager.clearConversation()
        }
    }

    fun clearActionLogs() {
        viewModelScope.launch {
            app.memoryManager.clearActionLogs()
        }
    }

    fun purgeAllData() {
        viewModelScope.launch {
            app.memoryManager.clearConversation()
            app.memoryManager.clearAllMemory()
            app.memoryManager.clearActionLogs()
        }
    }

    fun openAccessibilitySettings() {
        app.permissionManager.openAccessibilitySettings()
    }

    fun openNotificationListenerSettings() {
        app.permissionManager.openNotificationListenerSettings()
    }

    fun openAppSettings() {
        app.permissionManager.openAppSettings()
    }
}
