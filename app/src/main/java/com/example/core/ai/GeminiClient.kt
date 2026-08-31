package com.example.core.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.core.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed class GeminiResult {
    data class Success(
        val text: String?,
        val functionCalls: List<GeminiFunctionCall>,
        val thinkingText: String? = null,
        val rawResponse: GeminiGenerateResponse? = null
    ) : GeminiResult()

    data class Error(val message: String, val throwable: Throwable? = null) : GeminiResult()
}

class GeminiClient(
    private var config: GeminiConfig = GeminiConfig()
) {
    private val tag = "GeminiClient"

    fun updateConfig(newConfig: GeminiConfig) {
        this.config = newConfig
    }

    fun getConfig(): GeminiConfig = config

    /**
     * Executes an AI reasoning / function-calling cycle with Gemini.
     */
    suspend fun processTurn(
        conversationHistory: List<GeminiContent>,
        toolsDeclaration: List<GeminiToolDeclarationWrapper>? = null,
        image: Bitmap? = null,
        forceHighThinking: Boolean = false,
        modelIdOverride: String? = null
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.geminiApiKey
        if (!SecretProvider.isApiKeyConfigured) {
            return@withContext GeminiResult.Error(
                "Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel."
            )
        }

        val useHighThinking = forceHighThinking || config.highThinkingEnabled
        val model = if (!modelIdOverride.isNullOrBlank()) {
            modelIdOverride
        } else if (useHighThinking) {
            GeminiModel.PRO_THINKING.modelId
        } else {
            config.selectedModel.modelId
        }

        val generationConfig = if (useHighThinking) {
            // High Thinking Mode mandate: gemini-3.1-pro-preview, thinkingLevel = "HIGH", do not set maxOutputTokens
            GeminiGenerationConfig(
                temperature = config.temperature,
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "HIGH")
            )
        } else {
            GeminiGenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = 2048
            )
        }

        val contents = conversationHistory.toMutableList()

        // If an image is provided in the current turn, append it to the last user content
        if (image != null && contents.isNotEmpty()) {
            val lastContent = contents.last()
            val base64Image = bitmapToBase64(image)
            val updatedParts = lastContent.parts.toMutableList().apply {
                add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)))
            }
            contents[contents.size - 1] = lastContent.copy(parts = updatedParts)
        }

        val request = GeminiGenerateRequest(
            contents = contents,
            generationConfig = generationConfig,
            tools = toolsDeclaration,
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = config.systemInstruction))
            )
        )

        try {
            Log.d(tag, "Sending request to $model with ${contents.size} contents...")
            val response = GeminiNetworkProvider.apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            val parts = candidate?.content?.parts ?: emptyList()

            val textParts = mutableListOf<String>()
            val thinkingParts = mutableListOf<String>()
            val functionCalls = mutableListOf<GeminiFunctionCall>()

            for (part in parts) {
                if (part.functionCall != null) {
                    functionCalls.add(part.functionCall)
                } else if (part.thought == true && part.text != null) {
                    thinkingParts.add(part.text)
                } else if (part.text != null) {
                    textParts.add(part.text)
                }
            }

            val fullText = textParts.joinToString("\n").ifBlank { null }
            val fullThinking = thinkingParts.joinToString("\n").ifBlank { null }

            Log.d(tag, "Received response. text: $fullText, functionCalls: ${functionCalls.size}, thinking: ${fullThinking != null}")
            GeminiResult.Success(
                text = fullText,
                functionCalls = functionCalls,
                thinkingText = fullThinking,
                rawResponse = response
            )
        } catch (e: Exception) {
            Log.e(tag, "Gemini API error: ${e.message}", e)
            GeminiResult.Error("Gemini error: ${e.localizedMessage ?: e.message}", e)
        }
    }

    /**
     * Converts text to speech using gemini-3.1-flash-tts-preview
     */
    suspend fun generateSpeech(
        text: String,
        voiceName: String = config.ttsVoiceName
    ): ByteArray? = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.geminiApiKey
        if (!SecretProvider.isApiKeyConfigured || text.isBlank()) return@withContext null

        val ttsModel = GeminiModel.TTS_VOICE.modelId
        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = text))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = GeminiSpeechConfig(
                    voiceConfig = GeminiVoiceConfig(
                        prebuiltVoiceConfig = GeminiPrebuiltVoiceConfig(voiceName = voiceName)
                    )
                )
            )
        )

        try {
            Log.d(tag, "Requesting TTS for: $text with voice: $voiceName")
            val response = GeminiNetworkProvider.apiService.generateContent(
                model = ttsModel,
                apiKey = apiKey,
                request = request
            )

            val audioPart = response.candidates?.firstOrNull()
                ?.content?.parts
                ?.firstOrNull { it.inlineData != null }

            val base64Data = audioPart?.inlineData?.data
            if (base64Data != null) {
                return@withContext Base64.decode(base64Data, Base64.DEFAULT)
            }
            null
        } catch (e: Exception) {
            Log.w(tag, "Gemini TTS API call failed, will fallback to native TTS: ${e.message}")
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
