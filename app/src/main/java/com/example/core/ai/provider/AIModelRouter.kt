package com.example.core.ai.provider

import android.graphics.Bitmap
import android.util.Log
import com.example.core.ai.GeminiClient
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiResult
import com.example.core.ai.GeminiToolDeclarationWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIModelRouter(
    private val geminiClient: GeminiClient
) {
    private val tag = "AIModelRouter"

    val geminiProvider = GeminiProvider(geminiClient)
    val groqProvider = GroqProvider()
    val openRouterProvider = OpenRouterProvider()
    val grokProvider = GrokProvider()

    private val _activeProviderType = MutableStateFlow(AIProviderType.AUTO)
    val activeProviderType: StateFlow<AIProviderType> = _activeProviderType.asStateFlow()

    private val _selectedModels = MutableStateFlow<Map<AIProviderType, String>>(
        mapOf(
            AIProviderType.GEMINI to "gemini-2.5-flash",
            AIProviderType.GROQ to "llama-3.3-70b-versatile",
            AIProviderType.OPENROUTER to "anthropic/claude-3.5-sonnet",
            AIProviderType.GROK to "grok-2-latest"
        )
    )
    val selectedModels: StateFlow<Map<AIProviderType, String>> = _selectedModels.asStateFlow()

    private val _isAutoFallbackEnabled = MutableStateFlow(true)
    val isAutoFallbackEnabled: StateFlow<Boolean> = _isAutoFallbackEnabled.asStateFlow()

    private val _lastUsedEngine = MutableStateFlow("AUTO (Gemini → Groq → OpenRouter → Grok)")
    val lastUsedEngine: StateFlow<String> = _lastUsedEngine.asStateFlow()

    fun setProvider(providerType: AIProviderType) {
        _activeProviderType.value = providerType
        _lastUsedEngine.value = when (providerType) {
            AIProviderType.GEMINI -> "Gemini"
            AIProviderType.GROQ -> "Groq"
            AIProviderType.OPENROUTER -> "OpenRouter"
            AIProviderType.GROK -> "Grok (xAI)"
            AIProviderType.AUTO -> "AUTO (Gemini → Groq → OpenRouter → Grok)"
        }
    }

    fun setSelectedModel(providerType: AIProviderType, modelId: String) {
        val current = _selectedModels.value.toMutableMap()
        current[providerType] = modelId
        _selectedModels.value = current
    }

    fun setAutoFallback(enabled: Boolean) {
        _isAutoFallbackEnabled.value = enabled
    }

    fun getProvider(type: AIProviderType): AIProvider {
        return when (type) {
            AIProviderType.GEMINI -> geminiProvider
            AIProviderType.GROQ -> groqProvider
            AIProviderType.OPENROUTER -> openRouterProvider
            AIProviderType.GROK -> grokProvider
            AIProviderType.AUTO -> geminiProvider
        }
    }

    fun supportsRealtimeVoice(type: AIProviderType): Boolean {
        return type == AIProviderType.GEMINI || type == AIProviderType.AUTO
    }

    suspend fun getAllAvailableModels(): Map<AIProviderType, List<AIModelInfo>> {
        return mapOf(
            AIProviderType.GEMINI to geminiProvider.getAvailableModels(),
            AIProviderType.GROQ to groqProvider.getAvailableModels(),
            AIProviderType.OPENROUTER to openRouterProvider.getAvailableModels(),
            AIProviderType.GROK to grokProvider.getAvailableModels()
        )
    }

    /**
     * Executes conversational / agentic turn using the routed provider with intelligent fallback.
     */
    suspend fun processTurn(
        conversationHistory: List<GeminiContent>,
        toolsDeclaration: List<GeminiToolDeclarationWrapper>? = null,
        image: Bitmap? = null,
        temperature: Float = 0.4f,
        systemInstruction: String? = null
    ): GeminiResult {
        val mode = _activeProviderType.value

        if (mode != AIProviderType.AUTO) {
            val provider = getProvider(mode)
            val modelId = _selectedModels.value[mode] ?: ""
            _lastUsedEngine.value = "${provider.displayName} ($modelId)"
            return provider.processTurn(
                conversationHistory = conversationHistory,
                modelId = modelId,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )
        }

        // AUTO MODE: Try Gemini -> Groq -> OpenRouter -> Grok
        val geminiModel = _selectedModels.value[AIProviderType.GEMINI] ?: "gemini-2.5-flash"
        val groqModel = _selectedModels.value[AIProviderType.GROQ] ?: "llama-3.3-70b-versatile"
        val openRouterModel = _selectedModels.value[AIProviderType.OPENROUTER] ?: "anthropic/claude-3.5-sonnet"
        val grokModel = _selectedModels.value[AIProviderType.GROK] ?: "grok-2-latest"

        // 1. Try Gemini
        if (geminiProvider.isConfigured()) {
            _lastUsedEngine.value = "Gemini ($geminiModel)"
            val geminiResult = geminiProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = geminiModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (geminiResult) {
                is GeminiResult.Success -> return geminiResult
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(geminiResult.message)) {
                        return geminiResult
                    }
                    Log.w(tag, "Gemini unavailable (${geminiResult.message}). Failing over to Groq...")
                }
            }
        }

        // 2. Try Groq
        if (groqProvider.isConfigured()) {
            _lastUsedEngine.value = "Groq ($groqModel) [Failover]"
            val groqResult = groqProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = groqModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (groqResult) {
                is GeminiResult.Success -> return groqResult
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(groqResult.message)) {
                        return groqResult
                    }
                    Log.w(tag, "Groq unavailable (${groqResult.message}). Failing over to OpenRouter...")
                }
            }
        }

        // 3. Try OpenRouter
        if (openRouterProvider.isConfigured()) {
            _lastUsedEngine.value = "OpenRouter ($openRouterModel) [Failover]"
            val openRouterResult = openRouterProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = openRouterModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (openRouterResult) {
                is GeminiResult.Success -> return openRouterResult
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(openRouterResult.message)) {
                        return openRouterResult
                    }
                    Log.w(tag, "OpenRouter unavailable (${openRouterResult.message}). Failing over to Grok...")
                }
            }
        }

        // 4. Try Grok
        if (grokProvider.isConfigured()) {
            _lastUsedEngine.value = "Grok ($grokModel) [Failover]"
            val grokResult = grokProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = grokModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (grokResult) {
                is GeminiResult.Success -> return grokResult
                is GeminiResult.Error -> {
                    Log.e(tag, "Grok also unavailable (${grokResult.message}).")
                }
            }
        }

        _lastUsedEngine.value = "ALL PROVIDERS UNAVAILABLE"
        return GeminiResult.Error("All AI engines are temporarily unavailable. Please verify internet connection and API keys in Settings.")
    }

    /**
     * Checks whether an error is due to quota, rate limit, timeout, service unavailable, or network issue.
     */
    private fun isEligibleForFallback(errorMessage: String): Boolean {
        val lower = errorMessage.lowercase()
        return lower.contains("quota") ||
                lower.contains("429") ||
                lower.contains("rate limit") ||
                lower.contains("503") ||
                lower.contains("500") ||
                lower.contains("502") ||
                lower.contains("504") ||
                lower.contains("unavailable") ||
                lower.contains("timeout") ||
                lower.contains("connection") ||
                lower.contains("not configured") ||
                lower.contains("missing api key") ||
                lower.contains("resource exhausted") ||
                lower.contains("unreachable")
    }
}
