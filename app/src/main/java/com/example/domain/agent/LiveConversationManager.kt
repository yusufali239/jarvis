package com.example.domain.agent

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.core.ai.GeminiClient
import com.example.core.ai.GeminiConfig
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiFunctionCall
import com.example.core.ai.GeminiNetworkProvider
import com.example.core.ai.GeminiPart
import com.example.core.ai.GeminiPrebuiltVoiceConfig
import com.example.core.ai.GeminiSpeechConfig
import com.example.core.ai.GeminiVoiceConfig
import com.example.core.ai.LiveClientMessage
import com.example.core.ai.LiveFunctionResponseItem
import com.example.core.ai.LiveGenerationConfig
import com.example.core.ai.LiveMediaChunk
import com.example.core.ai.LiveRealtimeInput
import com.example.core.ai.LiveServerMessage
import com.example.core.ai.LiveSetupConfig
import com.example.core.ai.LiveToolResponse
import com.example.core.audio.JarvisAudioManager
import com.example.core.audio.VoiceRecognizer
import com.example.core.security.SecretProvider
import com.example.data.memory.MemoryManager
import com.example.domain.planner.TaskPlanner
import com.example.domain.tools.ToolRegistry
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the live bidirectional real-time conversation session with Gemini Live API via WebSockets.
 * If Gemini Live WebSocket is not supported by the network/endpoint, smoothly transitions to continuous
 * real-time conversational streaming loop while preserving bidirectional audio, barge-in interruption,
 * tool calling, memory, and simultaneous text/voice streaming.
 */
class LiveConversationManager(
    private val context: Context,
    private val audioManager: JarvisAudioManager,
    private val voiceRecognizer: VoiceRecognizer,
    private val geminiClient: GeminiClient,
    private val toolRegistry: ToolRegistry,
    private val memoryManager: MemoryManager,
    private val wakeWordDetector: com.example.core.audio.wakeword.WakeWordDetector,
    private val scope: CoroutineScope
) {
    private val tag = "LiveConvManager"

    private val _sessionState = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _liveStreamingAssistantText = MutableStateFlow("")
    val liveStreamingAssistantText: StateFlow<String> = _liveStreamingAssistantText.asStateFlow()

    private val _liveAudioAmplitude = MutableStateFlow(0f)
    val liveAudioAmplitude: StateFlow<Float> = _liveAudioAmplitude.asStateFlow()

    private val _activeTopic = MutableStateFlow("General Conversation")
    val activeTopic: StateFlow<String> = _activeTopic.asStateFlow()

    private var webSocket: WebSocket? = null
    private val isLiveActive = AtomicBoolean(false)
    private var isWebSocketConnected = false
    private var reconnectAttempts = 0
    private var fallbackLoopJob: Job? = null
    private var currentLiveSessionId: String = ""
    private val taskPlanner = TaskPlanner(toolRegistry)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val clientMessageAdapter = moshi.adapter(LiveClientMessage::class.java)
    private val serverMessageAdapter = moshi.adapter(LiveServerMessage::class.java)

    init {
        setupAudioCallbacks()
    }

    private fun setupAudioCallbacks() {
        // Forward live audio amplitude to UI
        scope.launch {
            audioManager.rmsAmplitude.collect { amp ->
                if (_sessionState.value == LiveSessionState.LISTENING || _sessionState.value == LiveSessionState.SPEAKING) {
                    _liveAudioAmplitude.value = amp
                }
            }
        }

        // Microphone chunk captured -> stream to Gemini Live WebSocket if connected
        audioManager.onAudioChunkCaptured = { audioChunk ->
            if (isWebSocketConnected && webSocket != null && isLiveActive.get()) {
                sendAudioChunk(audioChunk)
            }
        }

        // Interruption / Barge-in trigger from audio manager
        audioManager.onVoiceActivityDetected = {
            if (_sessionState.value == LiveSessionState.SPEAKING) {
                Log.d(tag, "Barge-in: Interrupting J.A.R.V.I.S. speech...")
                interruptCurrentSpeech()
            }
        }
    }

    /**
     * Starts Live Continuous Conversation Mode.
     */
    fun startLiveSession() {
        if (isLiveActive.get()) return

        wakeWordDetector.pauseForExternalAudio()
        isLiveActive.set(true)
        reconnectAttempts = 0
        currentLiveSessionId = memoryManager.startNewSession(
            provider = "Gemini Live",
            model = "gemini-2.0-flash-exp",
            isLive = true,
            title = "Live Voice Session"
        )
        _sessionState.value = LiveSessionState.CONNECTING
        _liveStreamingAssistantText.value = ""
        _livePartialTranscript.value = ""

        scope.launch {
            memoryManager.logAction(
                actionType = "LIVE_SESSION_START",
                description = "Initiated Realtime Continuous Live Conversation session.",
                status = "STARTED",
                riskLevel = "LOW"
            )

            // Strict audio resource handover: allow local recognizer to completely release AudioRecord
            kotlinx.coroutines.delay(200)
            connectToGeminiLive()
        }
    }

    /**
     * Stops and tears down the Live Conversation session.
     */
    fun endLiveSession() {
        isLiveActive.set(false)
        isWebSocketConnected = false
        fallbackLoopJob?.cancel()
        fallbackLoopJob = null

        audioManager.stopRecording()
        audioManager.stopPlayback()
        voiceRecognizer.stopListening()

        try {
            webSocket?.close(1000, "User ended live session")
        } catch (e: Exception) {
            Log.w(tag, "Error closing websocket: ${e.message}")
        } finally {
            webSocket = null
        }

        _sessionState.value = LiveSessionState.DISCONNECTED
        _liveAudioAmplitude.value = 0f

        scope.launch {
            memoryManager.logAction(
                actionType = "LIVE_SESSION_END",
                description = "Ended Realtime Live Conversation session.",
                status = "COMPLETED",
                riskLevel = "LOW"
            )

            // Strict audio resource handover: allow Live audio track to fully tear down before restarting wake word
            kotlinx.coroutines.delay(200)
            wakeWordDetector.resumeAfterExternalAudio()
        }
    }

    /**
     * Handles instant user interruption / barge-in.
     */
    fun interruptCurrentSpeech() {
        _sessionState.value = LiveSessionState.INTERRUPTED
        audioManager.stopPlayback()
        _sessionState.value = LiveSessionState.LISTENING

        scope.launch {
            memoryManager.logAction(
                actionType = "LIVE_BARGE_IN",
                description = "User interrupted AI speech; switched to listening.",
                status = "INTERRUPTED",
                riskLevel = "LOW"
            )
        }
    }

    private fun connectToGeminiLive() {
        val apiKey = SecretProvider.geminiApiKey
        if (!SecretProvider.isApiKeyConfigured) {
            _sessionState.value = LiveSessionState.ERROR
            _liveStreamingAssistantText.value = "Gemini API key is missing. Configure GEMINI_API_KEY in Secrets."
            return
        }

        // Live API WebSocket endpoint
        val liveUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"

        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(liveUrl)
            .build()

        try {
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(tag, "Gemini Live WebSocket opened successfully.")
                    isWebSocketConnected = true
                    reconnectAttempts = 0
                    _sessionState.value = LiveSessionState.CONNECTED

                    scope.launch {
                        // Send Setup Message
                        sendInitialSetup(ws)

                        // Start recording mic
                        audioManager.startRecording()
                        _sessionState.value = LiveSessionState.LISTENING
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleServerMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closing: $code / $reason")
                    isWebSocketConnected = false
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closed: $code / $reason")
                    isWebSocketConnected = false
                    if (isLiveActive.get()) {
                        handleReconnectOrFallback()
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(tag, "WebSocket failed: ${t.message}. Response: ${response?.message}")
                    isWebSocketConnected = false
                    if (isLiveActive.get()) {
                        handleReconnectOrFallback()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate WebSocket connection: ${e.message}", e)
            handleReconnectOrFallback()
        }
    }

    private suspend fun sendInitialSetup(ws: WebSocket) {
        val memoryContext = memoryManager.getFormattedMemoryContext()
        val systemPrompt = """
SYSTEM INSTRUCTION: Вы — J.A.R.V.I.S., ваш базовый язык — русский. Все ответы должны генерироваться строго на русском языке и быть оптимизированы для естественного чтения движком TTS на русском.

${geminiClient.getConfig().systemInstruction}

LIVE CONVERSATION PERSONALITY MANDATES:
1. Speak as J.A.R.V.I.S.: calm, intellectual, confident, concise, and futuristic in Russian.
2. Never say filler phrases like "Of course!", "Sure!", "With pleasure!".
3. Do not repeat the user's name or give long preambles.
4. Natural conversation: Understand pauses, short follow-up questions ("А на Луне?", "Объясни проще", "Подожди", "Короче"), and corrections without asking to repeat context.
5. If the user asks to open an app, read notifications, check screen, turn on flashlight, or run system actions, immediately invoke the corresponding tool function!
$memoryContext
        """.trimIndent()

        val setupConfig = LiveSetupConfig(
            model = "models/gemini-2.0-flash-exp",
            generationConfig = LiveGenerationConfig(
                responseModalities = listOf("AUDIO", "TEXT"),
                speechConfig = GeminiSpeechConfig(
                    voiceConfig = GeminiVoiceConfig(
                        prebuiltVoiceConfig = GeminiPrebuiltVoiceConfig(voiceName = geminiClient.getConfig().ttsVoiceName)
                    )
                ),
                temperature = 0.4f
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            tools = toolRegistry.getGeminiToolDeclarations()
        )

        val setupMessage = LiveClientMessage(setup = setupConfig)
        val jsonStr = clientMessageAdapter.toJson(setupMessage)
        ws.send(jsonStr)
        Log.d(tag, "Sent initial Live setup config.")
    }

    private fun sendAudioChunk(pcmChunk: ByteArray) {
        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val realtimeInput = LiveRealtimeInput(
                mediaChunks = listOf(
                    LiveMediaChunk(
                        mimeType = "audio/pcm;rate=16000",
                        data = base64Data
                    )
                )
            )
            val msg = LiveClientMessage(realtimeInput = realtimeInput)
            val json = clientMessageAdapter.toJson(msg)
            webSocket?.send(json)
        } catch (e: Exception) {
            Log.w(tag, "Failed to send audio chunk: ${e.message}")
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val serverMsg = serverMessageAdapter.fromJson(jsonText) ?: return

            // 1. Check for barge-in / interrupted flag from server
            if (serverMsg.serverContent?.interrupted == true) {
                Log.d(tag, "Server indicated barge-in interrupted.")
                interruptCurrentSpeech()
                return
            }

            // 2. Handle Model Response Parts (Audio and Text)
            val parts = serverMsg.serverContent?.modelTurn?.parts ?: emptyList()
            for (part in parts) {
                // Text streaming
                if (!part.text.isNullOrBlank()) {
                    _sessionState.value = LiveSessionState.SPEAKING
                    _liveStreamingAssistantText.value += part.text
                }

                // Audio streaming
                if (part.inlineData?.mimeType?.contains("audio") == true) {
                    _sessionState.value = LiveSessionState.SPEAKING
                    val pcmBytes = Base64.decode(part.inlineData.data, Base64.DEFAULT)
                    audioManager.enqueueAudioForPlayback(pcmBytes)
                }

                // Function Call via WebSocket
                if (part.functionCall != null) {
                    handleLiveFunctionCall(part.functionCall)
                }
            }

            // Check for toolCall block
            val functionCalls = serverMsg.toolCall?.functionCalls ?: emptyList()
            for (fc in functionCalls) {
                handleLiveFunctionCall(GeminiFunctionCall(name = fc.name, args = fc.args), fc.id)
            }

            if (serverMsg.serverContent?.turnComplete == true) {
                Log.d(tag, "Live turn complete.")
                if (_liveStreamingAssistantText.value.isNotBlank()) {
                    val fullText = _liveStreamingAssistantText.value
                    scope.launch {
                        memoryManager.saveChatMessage(
                            sessionId = currentLiveSessionId.ifBlank { memoryManager.currentActiveSessionId },
                            role = "assistant",
                            text = fullText,
                            audioAvailable = true,
                            provider = "Gemini Live",
                            model = "gemini-2.5-flash-native-audio"
                        )
                    }
                }
                _sessionState.value = LiveSessionState.LISTENING
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing server message: ${e.message}", e)
        }
    }

    private fun handleLiveFunctionCall(call: GeminiFunctionCall, callId: String? = null) {
        _sessionState.value = LiveSessionState.EXECUTING

        scope.launch {
            memoryManager.logAction(
                actionType = "LIVE_TOOL_CALL",
                description = "Executing live tool: ${call.name}",
                status = "EXECUTING",
                riskLevel = "LOW"
            )

            val toolResult = taskPlanner.executeStepWithRetry(call)

            memoryManager.logAction(
                actionType = "LIVE_TOOL_RESULT",
                description = "${call.name}: ${toolResult.summary}",
                status = if (toolResult.isSuccess) "SUCCESS" else "FAILED",
                riskLevel = "LOW"
            )

            // Send tool response back to Live WebSocket
            val toolResponseMsg = LiveClientMessage(
                toolResponse = LiveToolResponse(
                    functionResponses = listOf(
                        LiveFunctionResponseItem(
                            id = callId,
                            name = call.name,
                            response = mapOf(
                                "success" to toolResult.isSuccess,
                                "result" to toolResult.summary
                            )
                        )
                    )
                )
            )

            val json = clientMessageAdapter.toJson(toolResponseMsg)
            webSocket?.send(json)
        }
    }

    /**
     * Smooth Fallback / Continuous Realtime Loop:
     * When raw WebSockets are restricted or transiently reconnecting, ensures continuous
     * voice conversation never breaks, maintaining continuous listening, speech recognition,
     * tool calling, and streaming voice playback.
     */
    private fun handleReconnectOrFallback() {
        if (!isLiveActive.get()) return

        if (reconnectAttempts < 2) {
            reconnectAttempts++
            _sessionState.value = LiveSessionState.RECONNECTING
            _liveStreamingAssistantText.value = "Reconnecting to live audio stream..."
            scope.launch {
                delay(2000)
                connectToGeminiLive()
            }
        } else {
            Log.d(tag, "Switching to Native Continuous Voice Loop...")
            startContinuousNativeVoiceLoop()
        }
    }

    private fun startContinuousNativeVoiceLoop() {
        fallbackLoopJob?.cancel()
        fallbackLoopJob = scope.launch(Dispatchers.Main) {
            _sessionState.value = LiveSessionState.LISTENING

            voiceRecognizer.onResultReceived = { recognizedText ->
                if (isLiveActive.get()) {
                    processLiveVoiceUtterance(recognizedText)
                }
            }

            voiceRecognizer.onErrorOccurred = { _ ->
                if (isLiveActive.get()) {
                    // Automatically restart listening in continuous loop
                    scope.launch {
                        delay(500)
                        if (isLiveActive.get() && _sessionState.value == LiveSessionState.LISTENING) {
                            voiceRecognizer.startListening()
                        }
                    }
                }
            }

            voiceRecognizer.startListening()
        }
    }

    private fun processLiveVoiceUtterance(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isBlank()) {
            if (isLiveActive.get()) voiceRecognizer.startListening()
            return
        }

        _livePartialTranscript.value = trimmed
        _sessionState.value = LiveSessionState.THINKING
        _liveStreamingAssistantText.value = "Thinking..."

        scope.launch {
            try {
                // Save user message
                memoryManager.saveChatMessage(
                    sessionId = currentLiveSessionId.ifBlank { memoryManager.currentActiveSessionId },
                    role = "user",
                    text = trimmed,
                    audioAvailable = true,
                    provider = "Gemini Live",
                    model = "gemini-2.5-flash-native-audio"
                )
                memoryManager.logAction("LIVE_USER_SPOKEN", trimmed, "RECEIVED", "LOW")

                val recentHistory = memoryManager.getRecentMessages(12)
                val conversationHistory = mutableListOf<GeminiContent>()

                val memoryContext = memoryManager.getFormattedMemoryContext()
                if (memoryContext.isNotBlank()) {
                    conversationHistory.add(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = "[Memory: $memoryContext]"))
                        )
                    )
                    conversationHistory.add(
                        GeminiContent(
                            role = "model",
                            parts = listOf(GeminiPart(text = "Understood."))
                        )
                    )
                }

                for (item in recentHistory) {
                    conversationHistory.add(
                        GeminiContent(
                            role = if (item.role == "user") "user" else "model",
                            parts = listOf(GeminiPart(text = item.content))
                        )
                    )
                }

                if (conversationHistory.isEmpty() || conversationHistory.last().parts?.firstOrNull()?.text != trimmed) {
                    conversationHistory.add(
                        GeminiContent(role = "user", parts = listOf(GeminiPart(text = trimmed)))
                    )
                }

                val toolDeclarations = toolRegistry.getGeminiToolDeclarations()
                val result = geminiClient.processTurn(
                    conversationHistory = conversationHistory,
                    toolsDeclaration = toolDeclarations,
                    forceHighThinking = false
                )

                when (result) {
                    is com.example.core.ai.GeminiResult.Success -> {
                        if (result.functionCalls.isNotEmpty()) {
                            _sessionState.value = LiveSessionState.EXECUTING
                            for (call in result.functionCalls) {
                                val execResult = taskPlanner.executeStepWithRetry(call)
                                memoryManager.logAction("TOOL_RESULT", "${call.name}: ${execResult.summary}", "SUCCESS", "LOW")
                            }

                            // Follow up response
                            val finalResult = geminiClient.processTurn(
                                conversationHistory = conversationHistory,
                                toolsDeclaration = toolDeclarations
                            )
                            val respText = (finalResult as? com.example.core.ai.GeminiResult.Success)?.text ?: "Done."
                            streamLiveResponse(respText)
                        } else {
                            val respText = result.text ?: "I am ready."
                            streamLiveResponse(respText)
                        }
                    }
                    is com.example.core.ai.GeminiResult.Error -> {
                        _sessionState.value = LiveSessionState.ERROR
                        _liveStreamingAssistantText.value = result.message
                        if (isLiveActive.get()) {
                            delay(2000)
                            _sessionState.value = LiveSessionState.LISTENING
                            voiceRecognizer.startListening()
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(tag, "Live voice processing cancelled.")
            } catch (e: Exception) {
                Log.e(tag, "Error processing live utterance: ${e.message}", e)
                if (isLiveActive.get()) {
                    _sessionState.value = LiveSessionState.LISTENING
                    voiceRecognizer.startListening()
                }
            }
        }
    }

    private fun streamLiveResponse(fullText: String) {
        scope.launch {
            _sessionState.value = LiveSessionState.SPEAKING
            _liveStreamingAssistantText.value = fullText
            memoryManager.saveChatMessage(
                sessionId = currentLiveSessionId.ifBlank { memoryManager.currentActiveSessionId },
                role = "assistant",
                text = fullText,
                audioAvailable = true,
                provider = "Gemini Live",
                model = "gemini-2.5-flash-native-audio"
            )

            // Stream TTS audio
            val audioBytes = geminiClient.generateSpeech(fullText)
            if (audioBytes != null && audioBytes.isNotEmpty() && isLiveActive.get()) {
                audioManager.enqueueAudioForPlayback(audioBytes)
                // Wait for playback to complete or be interrupted
                while (audioManager.isPlaying.value && isLiveActive.get()) {
                    delay(50)
                }
            } else {
                delay(1200) // Brief pause
            }

            // Immediately continue listening without user having to press anything!
            if (isLiveActive.get()) {
                _sessionState.value = LiveSessionState.LISTENING
                voiceRecognizer.startListening()
            }
        }
    }
}
