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
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
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
            id = "gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            provider = AIProviderType.GEMINI,
            contextWindow = "2M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Flagship model with complex architectural and tool reasoning."
        ),
        AIModelInfo(
            id = "gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            provider = AIProviderType.GEMINI,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING,
                AICapability.REALTIME_AUDIO
            ),
            isRecommended = false,
            description = "High-speed reasoning model with native audio streaming support."
        ),
        AIModelInfo(
            id = "gemini-2.5-flash-native-audio-preview-12-2025",
            name = "Gemini 2.5 Live Native Audio",
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

    override suspend fun getAvailableModels(): List<AIModelInfo> = availableModels

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
