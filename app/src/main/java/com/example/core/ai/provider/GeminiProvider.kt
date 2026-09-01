package com.example.core.ai.provider

import android.graphics.Bitmap
import com.example.core.ai.GeminiClient
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiResult
import com.example.core.ai.GeminiToolDeclarationWrapper
import com.example.core.security.SecretProvider

class GeminiProvider(
    private val geminiClient: GeminiClient
) : AIProvider {
    override val providerType: AIProviderType = AIProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val availableModels = listOf(
        AIModelInfo(
            id = "gemini-3.5-flash",
            name = "Gemini 3.5 Flash",
            provider = AIProviderType.GEMINI,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = true,
            description = "Ultra-fast multimodal model with deep agentic tools and reasoning."
        ),
        AIModelInfo(
            id = "gemini-3.1-pro-preview",
            name = "Gemini 3.1 Pro",
            provider = AIProviderType.GEMINI,
            contextWindow = "2M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Flagship model with complex architectural and high thinking reasoning."
        ),
        AIModelInfo(
            id = "gemini-3.1-flash-lite-preview",
            name = "Gemini 3.1 Flash Lite",
            provider = AIProviderType.GEMINI,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Lightweight high-efficiency reasoning model."
        ),
        AIModelInfo(
            id = "gemini-flash-latest",
            name = "Gemini Flash (Latest)",
            provider = AIProviderType.GEMINI,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Always up to date flagship Gemini Flash model."
        ),
        AIModelInfo(
            id = "gemini-2.0-flash-exp",
            name = "Gemini 2.0 Flash Live Voice",
            provider = AIProviderType.GEMINI,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.REALTIME_AUDIO,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Low-latency bidirectional voice conversation engine."
        )
    )

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun getAvailableModels(): List<AIModelInfo> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val apiKey = SecretProvider.geminiApiKey
        if (apiKey.isNotBlank()) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val models = org.json.JSONObject(body).optJSONArray("models")
                    if (models != null && models.length() > 0) {
                        val dynamicList = mutableListOf<AIModelInfo>()
                        for (i in 0 until models.length()) {
                            val obj = models.getJSONObject(i)
                            val rawName = obj.optString("name")
                            val id = rawName.removePrefix("models/")
                            val displayName = obj.optString("displayName", id)
                            val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                            val supportsGenerateContent = supportedMethods?.toString()?.contains("generateContent") == true
                            if (id.isNotBlank() && supportsGenerateContent && !id.contains("embedding", ignoreCase = true) && !id.contains("aqa", ignoreCase = true)) {
                                dynamicList.add(
                                    AIModelInfo(
                                        id = id,
                                        name = displayName,
                                        provider = AIProviderType.GEMINI,
                                        contextWindow = "${obj.optInt("inputTokenLimit", 1000000) / 1000}k tokens",
                                        capabilities = setOf(AICapability.TEXT, AICapability.TOOL_CALLING, AICapability.STREAMING, AICapability.VISION),
                                        isRecommended = id == "gemini-3.5-flash" || id == "gemini-flash-latest",
                                        description = obj.optString("description", "Google Gemini model: $displayName")
                                    )
                                )
                            }
                        }
                        if (dynamicList.isNotEmpty()) return@withContext dynamicList
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiProvider", "Failed to fetch dynamic Gemini models: ${e.message}")
            }
        }
        availableModels
    }

    override suspend fun processTurn(
        conversationHistory: List<GeminiContent>,
        modelId: String,
        toolsDeclaration: List<GeminiToolDeclarationWrapper>?,
        image: Bitmap?,
        temperature: Float,
        systemInstruction: String?
    ): GeminiResult {
        return geminiClient.processTurn(
            conversationHistory = conversationHistory,
            toolsDeclaration = toolsDeclaration,
            image = image,
            modelIdOverride = modelId.takeIf { it.isNotBlank() }
        )
    }

    override fun isConfigured(): Boolean {
        return SecretProvider.isGeminiConfigured
    }

    override fun supportsCapability(capability: AICapability, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: availableModels.first()
        return model.capabilities.contains(capability)
    }
}
