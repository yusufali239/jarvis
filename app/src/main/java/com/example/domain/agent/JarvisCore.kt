package com.example.domain.agent

import android.graphics.Bitmap
import android.util.Log
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiFunctionCall
import com.example.core.ai.GeminiFunctionResponse
import com.example.core.ai.GeminiPart
import com.example.core.ai.GeminiResult
import com.example.core.ai.GeminiTtsManager
import com.example.core.ai.provider.AICapability
import com.example.core.ai.provider.AIModelRouter
import com.example.core.ai.provider.AIProviderType
import com.example.core.audio.VoiceRecognizer
import com.example.core.audio.wakeword.WakeWordDetector
import com.example.core.permissions.PermissionManager
import com.example.core.security.ActionRiskEngine
import com.example.core.security.RiskLevel
import com.example.data.memory.MemoryManager
import com.example.domain.planner.TaskPlanner
import com.example.domain.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingConfirmation(
    val functionCall: GeminiFunctionCall,
    val riskLevel: RiskLevel,
    val prompt: String,
    val onConfirm: suspend () -> Unit,
    val onCancel: () -> Unit
)

class JarvisCore(
    val aiModelRouter: AIModelRouter,
    private val toolRegistry: ToolRegistry,
    private val memoryManager: MemoryManager,
    private val ttsManager: GeminiTtsManager,
    private val voiceRecognizer: VoiceRecognizer,
    private val wakeWordDetector: WakeWordDetector,
    private val permissionManager: PermissionManager,
    private val scope: CoroutineScope
) {
    private val tag = "JarvisCore"

    private val _currentState = MutableStateFlow(JarvisState.IDLE)
    val currentState: StateFlow<JarvisState> = _currentState.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _lastAssistantResponse = MutableStateFlow("")
    val lastAssistantResponse: StateFlow<String> = _lastAssistantResponse.asStateFlow()

    private val _latestThinkingTrace = MutableStateFlow<String?>(null)
    val latestThinkingTrace: StateFlow<String?> = _latestThinkingTrace.asStateFlow()

    private val _isConversationMode = MutableStateFlow(false)
    val isConversationMode: StateFlow<Boolean> = _isConversationMode.asStateFlow()

    private var activeJob: Job? = null
    private val taskPlanner = TaskPlanner(toolRegistry)

    var onWakeWordActivated: (() -> Unit)? = null

    init {
        setupVoiceCallbacks()
        setupWakeWordCallbacks()
        refreshSystemStatus()
    }

    private fun setupVoiceCallbacks() {
        voiceRecognizer.onResultReceived = { spokenText ->
            processUserCommand(spokenText)
        }
        voiceRecognizer.onErrorOccurred = { errorMsg ->
            _currentState.value = JarvisState.ERROR
            _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = "Voice error: $errorMsg")
            scope.launch {
                memoryManager.logAction("VOICE_ERROR", errorMsg, "FAILED", "LOW")
            }
            wakeWordDetector.resumeAfterExternalAudio()
        }
    }

    private fun setupWakeWordCallbacks() {
        wakeWordDetector.onWakeWordDetected = {
            Log.i(tag, "Wake word detected in JarvisCore! Triggering activation callback.")
            onWakeWordActivated?.invoke()
        }
    }

    fun toggleConversationMode(enabled: Boolean? = null) {
        _isConversationMode.value = enabled ?: !_isConversationMode.value
    }

    fun refreshSystemStatus() {
        _systemStatus.value = _systemStatus.value.copy(
            isAccessibilityActive = permissionManager.isAccessibilityServiceEnabled(),
            isNotificationListenerActive = permissionManager.isNotificationListenerEnabled(),
            isVoiceOnline = voiceRecognizer.isAvailable(),
            isAiOnline = true,
            isToolsOnline = toolRegistry.getAllTools().isNotEmpty(),
            isMemoryOnline = true
        )
    }

    fun startListening() {
        wakeWordDetector.pauseForExternalAudio()
        ttsManager.stop()
        _currentState.value = JarvisState.LISTENING
        _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = "Listening for user voice...")
        voiceRecognizer.startListening()
    }

    fun stopListening() {
        voiceRecognizer.stopListening()
        if (_currentState.value == JarvisState.LISTENING) {
            _currentState.value = JarvisState.IDLE
        }
        wakeWordDetector.resumeAfterExternalAudio()
    }

    fun interrupt() {
        try {
            activeJob?.cancel()
            voiceRecognizer.stopListening()
            ttsManager.stop()
            _pendingConfirmation.value = null
            _currentState.value = JarvisState.IDLE
            _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = "Operation cancelled by user.")
            wakeWordDetector.resumeAfterExternalAudio()
            scope.launch {
                memoryManager.logAction("INTERRUPT", "User interrupted active process.", "CANCELLED", "LOW")
            }
        } catch (e: Exception) {
            Log.w(tag, "Interrupt error: ${e.message}")
        }
    }

    fun processUserCommand(
        userInput: String,
        image: Bitmap? = null,
        forceHighThinking: Boolean = false
    ) {
        val trimmedInput = userInput.trim()
        if (trimmedInput.isBlank() && image == null) return

        interrupt() // Clear any existing active job

        activeJob = scope.launch {
            try {
                // 1. Record User Input into Database
                val activeType = aiModelRouter.activeProviderType.value
                val activeModel = aiModelRouter.selectedModels.value[activeType] ?: "default"
                memoryManager.saveChatMessage(
                    sessionId = memoryManager.currentActiveSessionId,
                    role = "user",
                    text = trimmedInput.ifBlank { "[Image Multimodal Query]" },
                    provider = activeType.displayName,
                    model = activeModel
                )
                memoryManager.logAction(
                    actionType = "USER_COMMAND",
                    description = trimmedInput.ifBlank { "Sent image for analysis" },
                    status = "RECEIVED",
                    riskLevel = "LOW"
                )

                // 2. Set Status to Thinking / Planning
                _currentState.value = if (forceHighThinking) JarvisState.PLANNING else JarvisState.THINKING
                _systemStatus.value = _systemStatus.value.copy(
                    activeTaskDescription = "Reasoning intent: \"${trimmedInput.take(40)}...\""
                )

                // 3. Build Conversation Context & Memory Grounding
                val recentEntities = memoryManager.getRecentMessagesForSession(limit = 12)
                val memoryContext = memoryManager.getFormattedMemoryContext()

                val conversationHistory = mutableListOf<GeminiContent>()

                // Inject memory context if available
                if (memoryContext.isNotBlank()) {
                    conversationHistory.add(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = "[System Memory Grounding: $memoryContext]"))
                        )
                    )
                    conversationHistory.add(
                        GeminiContent(
                            role = "model",
                            parts = listOf(GeminiPart(text = "Memory context loaded. Understood."))
                        )
                    )
                }

                for (entity in recentEntities) {
                    val role = if (entity.role == "user") "user" else "model"
                    conversationHistory.add(
                        GeminiContent(
                            role = role,
                            parts = listOf(GeminiPart(text = entity.text))
                        )
                    )
                }

                // If conversation history doesn't end with user's current query, add it
                if (conversationHistory.isEmpty() || conversationHistory.last().parts.firstOrNull()?.text != trimmedInput) {
                    conversationHistory.add(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = trimmedInput.ifBlank { "Analyze this attached image in detail." }))
                        )
                    )
                }

                val toolDeclarations = toolRegistry.getGeminiToolDeclarations()

                // 4. Send Turn to Routed AI Provider (Gemini / Groq / OpenRouter / AUTO)
                val result = aiModelRouter.processTurn(
                    conversationHistory = conversationHistory,
                    toolsDeclaration = toolDeclarations,
                    image = image
                )

                when (result) {
                    is GeminiResult.Success -> {
                        _latestThinkingTrace.value = result.thinkingText

                        if (result.functionCalls.isNotEmpty()) {
                            // Execute tool calls
                            handleFunctionCalls(result.functionCalls, conversationHistory)
                        } else {
                            // Direct Textual Response
                            val responseText = result.text ?: "I am ready for your next instruction."
                            deliverResponse(responseText)
                        }
                    }
                    is GeminiResult.Error -> {
                        _currentState.value = JarvisState.ERROR
                        _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = result.message)
                        deliverResponse("I encountered an issue: ${result.message}")
                    }
                }
            } catch (e: CancellationException) {
                Log.d(tag, "Process turn cancelled.")
            } catch (e: Exception) {
                Log.e(tag, "Error in processUserCommand: ${e.message}", e)
                _currentState.value = JarvisState.ERROR
                _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = "Error: ${e.message}")
                deliverResponse("An unexpected error occurred: ${e.message}")
            }
        }
    }

    private suspend fun handleFunctionCalls(
        functionCalls: List<GeminiFunctionCall>,
        conversationHistory: MutableList<GeminiContent>
    ) {
        for (call in functionCalls) {
            val toolName = call.name
            val args = call.args ?: emptyMap()

            // Evaluate Risk Level
            val riskEval = ActionRiskEngine.evaluateAction(toolName, args)

            if (riskEval.requiresConfirmation) {
                // High risk tool requires explicit user confirmation
                _currentState.value = JarvisState.PLANNING
                _systemStatus.value = _systemStatus.value.copy(
                    activeTaskDescription = "Awaiting confirmation for $toolName"
                )

                _pendingConfirmation.value = PendingConfirmation(
                    functionCall = call,
                    riskLevel = riskEval.level,
                    prompt = riskEval.promptMessage ?: "Authorize J.A.R.V.I.S. to run $toolName with arguments: $args?",
                    onConfirm = {
                        _pendingConfirmation.value = null
                        executeSingleFunctionCallAndRespond(call, conversationHistory)
                    },
                    onCancel = {
                        _pendingConfirmation.value = null
                        deliverResponse("Action cancelled upon your request.")
                    }
                )
                return
            } else {
                executeSingleFunctionCallAndRespond(call, conversationHistory)
            }
        }
    }

    private suspend fun executeSingleFunctionCallAndRespond(
        call: GeminiFunctionCall,
        conversationHistory: MutableList<GeminiContent>
    ) {
        _currentState.value = JarvisState.EXECUTING
        _systemStatus.value = _systemStatus.value.copy(
            activeTaskDescription = "Executing tool: ${call.name}..."
        )

        memoryManager.logAction(
            actionType = "TOOL_CALL",
            description = "Calling ${call.name} with args ${call.args}",
            status = "EXECUTING",
            riskLevel = "LOW"
        )

        // Execute tool via TaskPlanner (with retry support)
        val toolResult = taskPlanner.executeStepWithRetry(call)

        _currentState.value = JarvisState.VERIFYING
        _systemStatus.value = _systemStatus.value.copy(
            activeTaskDescription = "Verifying ${call.name} result..."
        )

        memoryManager.logAction(
            actionType = "TOOL_RESULT",
            description = "${call.name}: ${toolResult.summary}",
            status = if (toolResult.isSuccess) "SUCCESS" else "FAILED",
            riskLevel = "LOW",
            details = toolResult.summary
        )

        // Append function call and function response into conversation turns for follow-up reasoning
        conversationHistory.add(
            GeminiContent(
                role = "model",
                parts = listOf(
                    GeminiPart(
                        functionCall = call
                    )
                )
            )
        )

        conversationHistory.add(
            GeminiContent(
                role = "user",
                parts = listOf(
                    GeminiPart(
                        functionResponse = GeminiFunctionResponse(
                            name = call.name,
                            response = mapOf(
                                "success" to toolResult.isSuccess,
                                "result" to toolResult.summary
                            )
                        )
                    )
                )
            )
        )

        // Request final concise conversational summary from routed AI
        val followUpResult = aiModelRouter.processTurn(
            conversationHistory = conversationHistory,
            toolsDeclaration = toolRegistry.getGeminiToolDeclarations()
        )

        when (followUpResult) {
            is GeminiResult.Success -> {
                val text = followUpResult.text ?: toolResult.summary
                deliverResponse(text)
            }
            is GeminiResult.Error -> {
                deliverResponse(toolResult.summary)
            }
        }
    }

    private fun deliverResponse(text: String) {
        scope.launch {
            _lastAssistantResponse.value = text
            _currentState.value = JarvisState.SPEAKING
            _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = text.take(60))

            // Save response in Memory
            val activeType = aiModelRouter.activeProviderType.value
            val activeModel = aiModelRouter.selectedModels.value[activeType] ?: "default"
            memoryManager.saveChatMessage(
                sessionId = memoryManager.currentActiveSessionId,
                role = "assistant",
                text = text,
                thinkingContent = _latestThinkingTrace.value,
                provider = activeType.displayName,
                model = activeModel
            )

            withContext(Dispatchers.Main) {
                ttsManager.speak(text) {
                    _currentState.value = JarvisState.IDLE
                    _systemStatus.value = _systemStatus.value.copy(activeTaskDescription = "Standing by.")

                    // If Conversation Mode is active, auto-trigger listening for follow up!
                    if (_isConversationMode.value) {
                        startListening()
                    } else {
                        wakeWordDetector.resumeAfterExternalAudio()
                    }
                }
            }
        }
    }
}
