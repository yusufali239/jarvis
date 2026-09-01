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
            AIProviderType.GROQ to "llama-3.1-8b-instant",
            AIProviderType.OPENROUTER to "google/gemini-2.0-flash-exp:free",
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
            val result = provider.processTurn(
                conversationHistory = conversationHistory,
                modelId = modelId,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )
            return when (result) {
                is GeminiResult.Success -> {
                    val displayName = result.respondingProvider ?: provider.displayName
                    _lastUsedEngine.value = displayName
                    result.copy(respondingProvider = displayName)
                }
                is GeminiResult.Error -> {
                    if (result.isRateLimit || result.statusCode == 429 || result.message.contains("429") || result.message.contains("quota", ignoreCase = true) || result.message.contains("resource exhausted", ignoreCase = true)) {
                        GeminiResult.Error(
                            message = "Превышен лимит запросов (HTTP 429). Включите режим AUTO или смените провайдера.",
                            throwable = result.throwable,
                            statusCode = 429,
                            isRateLimit = true
                        )
                    } else {
                        result
                    }
                }
            }
        }

        // AUTO MODE: Chain -> Gemini -> Groq -> OpenRouter -> Grok
        val geminiModel = _selectedModels.value[AIProviderType.GEMINI] ?: "gemini-2.5-flash"
        val groqModel = _selectedModels.value[AIProviderType.GROQ] ?: "llama-3.1-8b-instant"
        val openRouterModel = _selectedModels.value[AIProviderType.OPENROUTER] ?: "google/gemini-2.0-flash-exp:free"
        val grokModel = _selectedModels.value[AIProviderType.GROK] ?: "grok-2-latest"

        // 1. Primary: Gemini
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
                is GeminiResult.Success -> {
                    _lastUsedEngine.value = "Gemini"
                    return geminiResult.copy(respondingProvider = "Gemini", isFallback = false)
                }
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(geminiResult.message, geminiResult.statusCode, geminiResult.isRateLimit)) {
                        return geminiResult
                    }
                    Log.w(tag, "Gemini unavailable (status: ${geminiResult.statusCode}, msg: ${geminiResult.message}). Failing over to Groq...")
                }
            }
        } else {
            Log.d(tag, "Gemini not configured, skipping to Groq in AUTO mode.")
        }

        // 2. Fallback #1: Groq
        if (groqProvider.isConfigured()) {
            _lastUsedEngine.value = "Groq (Fallback)"
            val groqResult = groqProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = groqModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (groqResult) {
                is GeminiResult.Success -> {
                    _lastUsedEngine.value = "Groq (Fallback)"
                    return groqResult.copy(respondingProvider = "Groq (Fallback)", isFallback = true)
                }
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(groqResult.message, groqResult.statusCode, groqResult.isRateLimit)) {
                        return groqResult
                    }
                    Log.w(tag, "Groq unavailable (status: ${groqResult.statusCode}, msg: ${groqResult.message}). Failing over to OpenRouter...")
                }
            }
        } else {
            Log.d(tag, "Groq not configured, skipping to OpenRouter in AUTO mode.")
        }

        // 3. Fallback #2: OpenRouter
        if (openRouterProvider.isConfigured()) {
            _lastUsedEngine.value = "OpenRouter (Fallback)"
            val openRouterResult = openRouterProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = openRouterModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (openRouterResult) {
                is GeminiResult.Success -> {
                    _lastUsedEngine.value = "OpenRouter (Fallback)"
                    return openRouterResult.copy(respondingProvider = "OpenRouter (Fallback)", isFallback = true)
                }
                is GeminiResult.Error -> {
                    if (!_isAutoFallbackEnabled.value || !isEligibleForFallback(openRouterResult.message, openRouterResult.statusCode, openRouterResult.isRateLimit)) {
                        return openRouterResult
                    }
                    Log.w(tag, "OpenRouter unavailable (status: ${openRouterResult.statusCode}, msg: ${openRouterResult.message}). Failing over to Grok...")
                }
            }
        } else {
            Log.d(tag, "OpenRouter not configured, skipping to Grok in AUTO mode.")
        }

        // 4. Fallback #3: Grok (xAI)
        if (grokProvider.isConfigured()) {
            _lastUsedEngine.value = "Grok (Fallback)"
            val grokResult = grokProvider.processTurn(
                conversationHistory = conversationHistory,
                modelId = grokModel,
                toolsDeclaration = toolsDeclaration,
                image = image,
                temperature = temperature,
                systemInstruction = systemInstruction
            )

            when (grokResult) {
                is GeminiResult.Success -> {
                    _lastUsedEngine.value = "Grok (Fallback)"
                    return grokResult.copy(respondingProvider = "Grok (Fallback)", isFallback = true)
                }
                is GeminiResult.Error -> {
                    Log.e(tag, "Grok also unavailable (status: ${grokResult.statusCode}, msg: ${grokResult.message}).")
                }
            }
        } else {
            Log.d(tag, "Grok not configured in AUTO chain.")
        }

        _lastUsedEngine.value = "ALL PROVIDERS UNAVAILABLE"
        return GeminiResult.Error("Все ИИ-провайдеры временно недоступны или превысили лимиты. Проверьте подключение к сети и настройки API ключей.")
    }

    /**
     * Checks whether an error is due to quota, rate limit, 5xx server error, timeout, service unavailable, or network issue.
     */
    private fun isEligibleForFallback(
        errorMessage: String,
        statusCode: Int? = null,
        isRateLimit: Boolean = false
    ): Boolean {
        if (isRateLimit) return true
        if (statusCode != null && (statusCode == 429 || statusCode in 500..599 || statusCode == 404 || statusCode == 408)) {
            return true
        }
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
                lower.contains("unreachable") ||
                lower.contains("failed to connect") ||
                lower.contains("sockettimeout") ||
                lower.contains("unknownhost")
    }
}
