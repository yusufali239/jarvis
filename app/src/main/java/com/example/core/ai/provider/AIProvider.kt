package com.example.core.ai.provider

import android.graphics.Bitmap
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiResult
import com.example.core.ai.GeminiToolDeclarationWrapper

interface AIProvider {
    val providerType: AIProviderType
    val displayName: String

    suspend fun getAvailableModels(): List<AIModelInfo>

    suspend fun processTurn(
        conversationHistory: List<GeminiContent>,
        modelId: String,
        toolsDeclaration: List<GeminiToolDeclarationWrapper>? = null,
        image: Bitmap? = null,
        temperature: Float = 0.4f,
        systemInstruction: String? = null
    ): GeminiResult

    fun isConfigured(): Boolean

    fun supportsCapability(capability: AICapability, modelId: String): Boolean
}
