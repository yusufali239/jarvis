package com.example.core.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.core.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class GeminiTtsManager(
    private val context: Context,
    private val geminiClient: GeminiClient,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private val tag = "GeminiTtsManager"
    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsReady = false
    private val audioPlayer = AudioPlayer(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _diagnosticState = MutableStateFlow(TtsDiagnosticState())
    val diagnosticState: StateFlow<TtsDiagnosticState> = _diagnosticState.asStateFlow()

    private var currentEnginePreference = TtsEnginePreference.AUTO_BEST
    private var currentLanguagePreference = TtsLanguagePreference.AUTO_DETECT
    private var selectedVoiceName: String? = null

    init {
        initializeTtsEngine(currentEnginePreference)
    }

    /**
     * Initializes or re-initializes the TTS engine according to preference.
     */
    fun setEnginePreference(preference: TtsEnginePreference) {
        currentEnginePreference = preference
        initializeTtsEngine(preference)
    }

    fun setLanguagePreference(preference: TtsLanguagePreference) {
        currentLanguagePreference = preference
    }

    fun setSelectedVoice(voiceName: String?) {
        selectedVoiceName = voiceName
        applySelectedVoice()
    }

    private fun initializeTtsEngine(preference: TtsEnginePreference) {
        scope.launch(Dispatchers.Main) {
            try {
                nativeTts?.stop()
                nativeTts?.shutdown()
            } catch (e: Exception) {
                Log.w(tag, "Cleanup error on re-init: ${e.message}")
            }
            nativeTts = null
            isNativeTtsReady = false

            _diagnosticState.value = _diagnosticState.value.copy(
                statusSummary = "INITIALIZING",
                isReady = false
            )

            try {
                val targetPackage = when (preference) {
                    TtsEnginePreference.AUTO_BEST -> determineBestEnginePackage()
                    TtsEnginePreference.GOOGLE_TTS -> "com.google.android.tts"
                    TtsEnginePreference.SAMSUNG_TTS -> "com.samsung.SMT"
                    TtsEnginePreference.SYSTEM_DEFAULT -> null
                }

                if (targetPackage != null) {
                    Log.d(tag, "Initializing TextToSpeech with specific engine package: $targetPackage")
                    nativeTts = TextToSpeech(context, this@GeminiTtsManager, targetPackage)
                } else {
                    Log.d(tag, "Initializing TextToSpeech with system default engine")
                    nativeTts = TextToSpeech(context, this@GeminiTtsManager)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to instantiate TextToSpeech: ${e.message}", e)
                _diagnosticState.value = _diagnosticState.value.copy(
                    statusSummary = "ERROR",
                    diagnosticMessage = "TTS initialization failed: ${e.message}"
                )
            }
        }
    }

    private fun determineBestEnginePackage(): String? {
        // Query installed TTS engines
        try {
            val tempTts = TextToSpeech(context, null)
            val engines = tempTts.engines ?: emptyList()
            val enginePkgs = engines.map { it.name }
            tempTts.shutdown()

            // Google Speech Services usually has high-quality neural Russian voices preinstalled
            if (enginePkgs.contains("com.google.android.tts")) {
                return "com.google.android.tts"
            }
            // If on Samsung and Samsung TTS is present
            if (enginePkgs.contains("com.samsung.SMT")) {
                return "com.samsung.SMT"
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not inspect engines: ${e.message}")
        }
        return null // Fallback to system default
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS && nativeTts != null) {
            isNativeTtsReady = true
            inspectAndConfigureTts()
        } else {
            isNativeTtsReady = false
            Log.e(tag, "TextToSpeech init failed with status: $status")
            _diagnosticState.value = _diagnosticState.value.copy(
                isReady = false,
                statusSummary = "INIT_FAILED",
                diagnosticMessage = "TextToSpeech onInit returned status code $status"
            )
        }
    }

    private fun inspectAndConfigureTts() {
        val tts = nativeTts ?: return
        val activeEnginePkg = tts.defaultEngine ?: ""
        val enginesList = tts.engines ?: emptyList()

        val engineInfoList = enginesList.map { engine ->
            TtsEngineInfo(
                name = engine.label ?: engine.name,
                packageName = engine.name,
                isDefault = engine.name == activeEnginePkg,
                isCurrent = engine.name == activeEnginePkg
            )
        }

        // Test Russian Language support on current engine
        val ruLocale = Locale("ru", "RU")
        val ruSupportCode = tts.isLanguageAvailable(ruLocale)

        val (isRuSupported, ruStatus) = when (ruSupportCode) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> Pair(true, TtsLanguageSupportStatus.AVAILABLE)
            TextToSpeech.LANG_MISSING_DATA -> Pair(false, TtsLanguageSupportStatus.MISSING_DATA)
            TextToSpeech.LANG_NOT_SUPPORTED -> Pair(false, TtsLanguageSupportStatus.NOT_SUPPORTED)
            else -> Pair(false, TtsLanguageSupportStatus.UNKNOWN)
        }

        // Extract available voices
        val voiceList = mutableListOf<TtsVoiceInfo>()
        var chosenVoice: Voice? = null

        try {
            val deviceVoices = tts.voices
            if (!deviceVoices.isNullOrEmpty()) {
                for (v in deviceVoices) {
                    val isRu = v.locale.language.equals("ru", ignoreCase = true)
                    voiceList.add(
                        TtsVoiceInfo(
                            name = v.name,
                            localeTag = v.locale.toLanguageTag(),
                            quality = if (v.quality >= Voice.QUALITY_VERY_HIGH) "Very High" else if (v.quality >= Voice.QUALITY_HIGH) "High" else "Normal",
                            isNetwork = v.isNetworkConnectionRequired,
                            isSelected = false
                        )
                    )
                    // Auto-select preferred Russian voice
                    if (isRu && chosenVoice == null && !v.isNetworkConnectionRequired) {
                        chosenVoice = v
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not query TTS voices: ${e.message}")
        }

        // Apply Russian voice if available
        if (isRuSupported) {
            if (chosenVoice != null) {
                try {
                    tts.voice = chosenVoice
                } catch (e: Exception) {
                    tts.setLanguage(ruLocale)
                }
            } else {
                tts.setLanguage(ruLocale)
            }
        }

        val activeVoiceName = tts.voice?.name ?: if (isRuSupported) "System Russian Voice" else "Default Engine Voice"
        val activeEngineDisplayName = when {
            activeEnginePkg.contains("google", ignoreCase = true) -> "Google Speech Services"
            activeEnginePkg.contains("samsung", ignoreCase = true) -> "Samsung Text-to-Speech"
            else -> activeEnginePkg.ifBlank { "System Default Engine" }
        }

        val statusSummary = if (isRuSupported) "READY" else if (ruStatus == TtsLanguageSupportStatus.MISSING_DATA) "MISSING_RUSSIAN_VOICE" else "NOT_SUPPORTED"
        val diagnosticMsg = when {
            isRuSupported -> "Russian TTS voice is fully operational on $activeEngineDisplayName."
            ruStatus == TtsLanguageSupportStatus.MISSING_DATA -> "Russian voice data is not downloaded in $activeEngineDisplayName. Open Settings to download Russian voice package."
            else -> "Russian language is not supported by $activeEngineDisplayName. Please switch to Google Speech Services in Settings."
        }

        _diagnosticState.value = TtsDiagnosticState(
            activeEngineName = activeEngineDisplayName,
            activeEnginePackage = activeEnginePkg,
            installedEngines = engineInfoList,
            isRussianSupported = isRuSupported,
            russianSupportStatus = ruStatus,
            activeVoiceName = activeVoiceName,
            activeLocale = "ru-RU",
            isReady = true,
            statusSummary = statusSummary,
            availableVoices = voiceList,
            diagnosticMessage = diagnosticMsg
        )

        Log.i(tag, "TTS Diagnostic: Engine=$activeEngineDisplayName, Russian=$ruStatus, Voice=$activeVoiceName")
    }

    private fun applySelectedVoice() {
        val voiceName = selectedVoiceName ?: return
        val tts = nativeTts ?: return
        try {
            val matching = tts.voices?.firstOrNull { it.name == voiceName }
            if (matching != null) {
                tts.voice = matching
                _diagnosticState.value = _diagnosticState.value.copy(activeVoiceName = matching.name)
                Log.d(tag, "Applied selected voice: ${matching.name}")
            }
        } catch (e: Exception) {
            Log.w(tag, "Error applying selected voice: ${e.message}")
        }
    }

    fun speak(
        text: String,
        preferGeminiTts: Boolean = true,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // Clean up markdown markers for speech
        val cleanedText = text
            .replace(Regex("\\*\\*|\\*|`|#|>|~~"), "")
            .trim()

        scope.launch {
            if (preferGeminiTts) {
                try {
                    val audioBytes = geminiClient.generateSpeech(cleanedText)
                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            requestAssistantAudioFocus()
                            audioPlayer.playAudioBytes(audioBytes) {
                                abandonAssistantAudioFocus()
                                onDone?.invoke()
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Gemini Cloud TTS failed, falling back to Native Local TTS: ${e.message}")
                }
            }

            // Fallback to Native TTS with rigorous Language Detection
            withContext(Dispatchers.Main) {
                speakNativeWithLanguageDetection(cleanedText, onDone)
            }
        }
    }

    private fun speakNativeWithLanguageDetection(text: String, onDone: (() -> Unit)?) {
        val tts = nativeTts
        if (!isNativeTtsReady || tts == null) {
            Log.w(tag, "Native TTS not ready for playback.")
            onDone?.invoke()
            return
        }

        requestAssistantAudioFocus()

        // 1. Language Detection
        val targetLanguage = when (currentLanguagePreference) {
            TtsLanguagePreference.FORCE_RUSSIAN -> DetectedLanguage.RUSSIAN
            TtsLanguagePreference.FORCE_ENGLISH -> DetectedLanguage.ENGLISH
            TtsLanguagePreference.FORCE_UZBEK -> DetectedLanguage.UZBEK
            TtsLanguagePreference.AUTO_DETECT -> LanguageDetector.detectLanguage(text)
        }

        Log.d(tag, "Target language for utterance detected as: ${targetLanguage.displayName} (${targetLanguage.tag})")

        // 2. Select appropriate Voice/Locale for the target language
        when (targetLanguage) {
            DetectedLanguage.RUSSIAN -> {
                val ruLocale = Locale("ru", "RU")
                val isRuAvailable = tts.isLanguageAvailable(ruLocale) >= TextToSpeech.LANG_AVAILABLE
                if (isRuAvailable) {
                    // Try to set specific Russian Voice if available
                    val ruVoice = tts.voices?.firstOrNull { it.locale.language.equals("ru", ignoreCase = true) }
                    if (ruVoice != null) {
                        try {
                            tts.voice = ruVoice
                        } catch (e: Exception) {
                            tts.setLanguage(ruLocale)
                        }
                    } else {
                        tts.setLanguage(ruLocale)
                    }
                } else {
                    Log.e(tag, "CRITICAL: Russian voice missing on active TTS engine! Refusing to read in English accent.")
                    _diagnosticState.value = _diagnosticState.value.copy(
                        statusSummary = "MISSING_RUSSIAN_VOICE",
                        diagnosticMessage = "Russian TTS data missing on device. Please install Russian speech data."
                    )
                }
            }
            DetectedLanguage.ENGLISH -> {
                val enLocale = Locale("en", "US")
                val enVoice = tts.voices?.firstOrNull { it.locale.language.equals("en", ignoreCase = true) }
                if (enVoice != null) {
                    try {
                        tts.voice = enVoice
                    } catch (e: Exception) {
                        tts.setLanguage(enLocale)
                    }
                } else {
                    tts.setLanguage(enLocale)
                }
            }
            DetectedLanguage.UZBEK -> {
                val uzLocale = Locale("uz", "UZ")
                val isUzAvailable = tts.isLanguageAvailable(uzLocale) >= TextToSpeech.LANG_AVAILABLE
                if (isUzAvailable) {
                    tts.setLanguage(uzLocale)
                } else {
                    // Fallback to Russian if Uzbek voice is absent
                    tts.setLanguage(Locale("ru", "RU"))
                }
            }
            DetectedLanguage.UNKNOWN -> {
                tts.setLanguage(Locale("ru", "RU"))
            }
        }

        // 3. Queue playback
        val utteranceId = "jarvis_tts_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                scope.launch(Dispatchers.Main) {
                    abandonAssistantAudioFocus()
                    onDone?.invoke()
                }
            }
            override fun onError(id: String?) {
                scope.launch(Dispatchers.Main) {
                    abandonAssistantAudioFocus()
                    onDone?.invoke()
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun requestAssistantAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAssistantAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to abandon audio focus: ${e.message}")
        }
    }

    fun stop() {
        audioPlayer.stop()
        abandonAssistantAudioFocus()
        try {
            nativeTts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping native TTS: ${e.message}")
        }
    }

    fun release() {
        stop()
        try {
            nativeTts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down native TTS: ${e.message}")
        }
        nativeTts = null
    }
}
