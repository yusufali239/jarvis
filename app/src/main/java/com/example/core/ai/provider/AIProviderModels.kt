package com.example.core.ai.provider

enum class AICapability(val label: String) {
    TEXT("Text"),
    VISION("Vision"),
    TOOL_CALLING("Tools"),
    STREAMING("Streaming"),
    REALTIME_AUDIO("Live Voice")
}

enum class AIProviderType(val displayName: String) {
    GEMINI("Gemini"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter"),
    GROK("Grok"),
    AUTO("AUTO")
}

data class AIModelInfo(
    val id: String,
    val name: String,
    val provider: AIProviderType,
    val contextWindow: String,
    val capabilities: Set<AICapability>,
    val isRecommended: Boolean = false,
    val description: String = ""
)
