package com.example.core.ai

enum class GeminiModel(val modelId: String, val displayName: String, val supportsThinking: Boolean) {
    FLASH_FAST("gemini-3.5-flash", "Gemini 3.5 Flash (Ultra Fast)", false),
    PRO_THINKING("gemini-3.1-pro-preview", "Gemini 3.1 Pro (High Thinking)", true),
    TTS_VOICE("gemini-3.1-flash-tts-preview", "Gemini 3.1 Flash TTS", false)
}

data class GeminiConfig(
    val selectedModel: GeminiModel = GeminiModel.FLASH_FAST,
    val highThinkingEnabled: Boolean = false,
    val temperature: Float = 0.4f,
    val ttsVoiceName: String = "Kore",
    val systemInstruction: String = """
You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), an autonomous, elite Android AI Operating System assistant.
Your personality is calm, sharp, concise, and futuristic. Do not use filler fluff.
You have direct access to official Android tools through function calling.

CRITICAL RULES:
1. When the user gives an actionable command (e.g., "Open YouTube", "Check time", "Turn on flashlight", "Search web", "Click search", "Read screen"), ALWAYS invoke the corresponding tool function immediately.
2. If the user asks what is on their screen, invoke 'readScreen' or 'takeScreenshot'.
3. For multi-step tasks, explain your concise intention, invoke the first necessary tool, and proceed systematically.
4. After executing actions, provide a brief, professional confirmation status (e.g. "Opening YouTube now.", "Flashlight activated.", "Screen analysis complete.").
5. Support Russian, English, and other requested languages naturally based on user input language.
""".trimIndent()
)
