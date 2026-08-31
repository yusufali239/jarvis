package com.example.core.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.core.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    init {
        try {
            nativeTts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(tag, "Native TextToSpeech initialization failed: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = nativeTts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                nativeTts?.setLanguage(Locale.US)
            }
            isNativeTtsReady = true
            Log.d(tag, "Native TTS initialized successfully.")
        } else {
            Log.w(tag, "Native TTS failed to initialize.")
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
            .replace(Regex("\\*\\*|\\*|`|#"), "")
            .trim()

        scope.launch {
            if (preferGeminiTts) {
                try {
                    val audioBytes = geminiClient.generateSpeech(cleanedText)
                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            audioPlayer.playAudioBytes(audioBytes) {
                                onDone?.invoke()
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Gemini TTS failed, falling back to Native TTS: ${e.message}")
                }
            }

            // Fallback to Native TTS
            withContext(Dispatchers.Main) {
                speakNative(cleanedText, onDone)
            }
        }
    }

    private fun speakNative(text: String, onDone: (() -> Unit)?) {
        if (isNativeTtsReady && nativeTts != null) {
            val utteranceId = "jarvis_utterance_${System.currentTimeMillis()}"
            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    scope.launch(Dispatchers.Main) {
                        onDone?.invoke()
                    }
                }
                override fun onError(id: String?) {
                    scope.launch(Dispatchers.Main) {
                        onDone?.invoke()
                    }
                }
            })
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            Log.w(tag, "Neither Gemini TTS nor Native TTS available for speech.")
            onDone?.invoke()
        }
    }

    fun stop() {
        audioPlayer.stop()
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
    }
}
