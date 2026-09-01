package com.example

import android.app.Application
import com.example.android.apps.AppManager
import com.example.android.system.SystemController
import com.example.core.ai.GeminiClient
import com.example.core.ai.GeminiConfig
import com.example.core.ai.GeminiTtsManager
import com.example.core.ai.provider.AIModelRouter
import com.example.core.audio.JarvisAudioManager
import com.example.core.audio.VoiceRecognizer
import com.example.core.audio.wakeword.WakeWordDetector
import com.example.core.permissions.PermissionManager
import com.example.core.security.SecretProvider
import com.example.data.memory.JarvisDatabase
import com.example.data.memory.MemoryManager
import com.example.domain.agent.JarvisCore
import com.example.domain.agent.LiveConversationManager
import com.example.domain.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JarvisApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: JarvisDatabase
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var appManager: AppManager
        private set

    lateinit var systemController: SystemController
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var geminiClient: GeminiClient
        private set

    lateinit var geminiConfig: GeminiConfig
        private set

    lateinit var aiModelRouter: AIModelRouter
        private set

    lateinit var wakeWordDetector: WakeWordDetector
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var ttsManager: GeminiTtsManager
        private set

    lateinit var voiceRecognizer: VoiceRecognizer
        private set

    lateinit var audioManager: JarvisAudioManager
        private set

    lateinit var liveConversationManager: LiveConversationManager
        private set

    lateinit var jarvisCore: JarvisCore
        private set

    override fun onCreate() {
        super.onCreate()

        SecretProvider.initialize(this)

        database = JarvisDatabase.getInstance(this)
        memoryManager = MemoryManager(database.jarvisDao())
        appManager = AppManager(this)
        systemController = SystemController(this)
        permissionManager = PermissionManager(this)

        geminiConfig = GeminiConfig()
        geminiClient = GeminiClient(geminiConfig)
        aiModelRouter = AIModelRouter(geminiClient)

        wakeWordDetector = WakeWordDetector(this, applicationScope)

        toolRegistry = ToolRegistry(
            context = this,
            appManager = appManager,
            systemController = systemController,
            memoryManager = memoryManager
        )

        ttsManager = GeminiTtsManager(
            context = this,
            geminiClient = geminiClient,
            scope = applicationScope
        )

        voiceRecognizer = VoiceRecognizer(this)
        audioManager = JarvisAudioManager(this, applicationScope)

        liveConversationManager = LiveConversationManager(
            context = this,
            audioManager = audioManager,
            voiceRecognizer = voiceRecognizer,
            geminiClient = geminiClient,
            toolRegistry = toolRegistry,
            memoryManager = memoryManager,
            wakeWordDetector = wakeWordDetector,
            scope = applicationScope
        )

        jarvisCore = JarvisCore(
            aiModelRouter = aiModelRouter,
            toolRegistry = toolRegistry,
            memoryManager = memoryManager,
            ttsManager = ttsManager,
            voiceRecognizer = voiceRecognizer,
            wakeWordDetector = wakeWordDetector,
            permissionManager = permissionManager,
            scope = applicationScope
        )

        // Wake word trigger -> Starts voice listening / Live Conversation
        jarvisCore.onWakeWordActivated = {
            ttsManager.speak("Слушаю вас, сэр.") {
                jarvisCore.startListening()
            }
        }

        // Start listening for wake word if enabled
        wakeWordDetector.startListening()
    }

    override fun onTerminate() {
        super.onTerminate()
        wakeWordDetector.stopListening()
        liveConversationManager.endLiveSession()
        audioManager.release()
        ttsManager.release()
        voiceRecognizer.stopListening()
    }
}
