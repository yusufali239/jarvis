package com.example.core.audio.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class WakeWordState(val label: String) {
    ACTIVE("ACTIVE (STANDBY)"),
    DETECTED("WAKE WORD DETECTED!"),
    PAUSED("PAUSED (LIVE IN PROGRESS)"),
    DISABLED("DISABLED")
}

class WakeWordDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "WakeWordDetector"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WakeWordState.DISABLED)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _detectedPhrase = MutableStateFlow<String?>(null)
    val detectedPhrase: StateFlow<String?> = _detectedPhrase.asStateFlow()

    var onWakeWordDetected: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val isRunning = AtomicBoolean(false)
    private val isPausedForLive = AtomicBoolean(false)
    private var restartJob: Job? = null

    private val wakePhrases = listOf(
        "hey jarvis",
        "jarvis",
        "джарвис",
        "хей джарвис",
        "эй джарвис",
        "окей джарвис",
        "слушай джарвис",
        "hi jarvis",
        "ok jarvis",
        "hello jarvis"
    )

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) {
            if (!isPausedForLive.get()) {
                startListening()
            }
        } else {
            stopListening()
            _state.value = WakeWordState.DISABLED
        }
    }

    /**
     * Starts the local wake word listener in standby.
     */
    fun startListening() {
        if (!isEnabled.value) {
            _state.value = WakeWordState.DISABLED
            return
        }
        if (isRunning.get() || isPausedForLive.get()) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Local speech recognition is not available for wake word detection.")
            _state.value = WakeWordState.DISABLED
            return
        }

        mainHandler.post {
            try {
                cleanupRecognizer()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                speechRecognizer?.startListening(intent)
                isRunning.set(true)
                _state.value = WakeWordState.ACTIVE
                Log.d(tag, "Wake word detector is now ACTIVE (listening for 'Hey JARVIS').")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start wake word detector: ${e.message}", e)
                isRunning.set(false)
                scheduleRestart(2000)
            }
        }
    }

    /**
     * Pauses wake word detection to release microphone for Live Conversation or voice recording.
     */
    fun pauseForExternalAudio() {
        isPausedForLive.set(true)
        stopListening()
        _state.value = WakeWordState.PAUSED
        Log.d(tag, "Wake word detector PAUSED. Microphone released for Live Voice.")
    }

    /**
     * Resumes wake word detection once Live Conversation or voice recording has finished.
     */
    fun resumeAfterExternalAudio() {
        isPausedForLive.set(false)
        if (isEnabled.value) {
            _state.value = WakeWordState.ACTIVE
            scheduleRestart(500)
        } else {
            _state.value = WakeWordState.DISABLED
        }
        Log.d(tag, "Wake word detector RESUMED after external audio.")
    }

    fun stopListening() {
        isRunning.set(false)
        restartJob?.cancel()
        restartJob = null
        mainHandler.post {
            cleanupRecognizer()
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error cleaning up speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            isRunning.set(false)
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isRunning.set(false)
            if (isPausedForLive.get() || !isEnabled.value) return

            // Don't log spam on normal no-match timeouts in standby
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                scheduleRestart(300)
            } else {
                Log.d(tag, "Wake word recognizer error ($error). Restarting in 1s...")
                scheduleRestart(1000)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkWakeWordMatches(matches)
            if (isRunning.get() && !isPausedForLive.get() && isEnabled.value) {
                scheduleRestart(200)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkWakeWordMatches(matches)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkWakeWordMatches(matches: List<String>?) {
        if (matches.isNullOrEmpty() || isPausedForLive.get() || !isEnabled.value) return

        for (match in matches) {
            val clean = match.lowercase(Locale.getDefault()).trim()
            for (phrase in wakePhrases) {
                if (clean.contains(phrase)) {
                    Log.i(tag, "🎯 WAKE WORD DETECTED: '$phrase' in '$match'!")
                    _detectedPhrase.value = match
                    _state.value = WakeWordState.DETECTED

                    // Immediately pause wake detector to free microphone for Live Session
                    pauseForExternalAudio()

                    scope.launch(Dispatchers.Main) {
                        onWakeWordDetected?.invoke()
                    }
                    return
                }
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isPausedForLive.get() || !isEnabled.value) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            if (!isPausedForLive.get() && isEnabled.value && !isRunning.get()) {
                startListening()
            }
        }
    }
}
