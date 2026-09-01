package com.example.core.ai

enum class GeminiModel(val modelId: String, val displayName: String, val supportsThinking: Boolean) {
    FLASH_FAST("gemini-3.5-flash", "Gemini 3.5 Flash (Ultra Fast)", false),
    PRO_THINKING("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Deep Reasoning)", true),
    FLASH_LITE("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", false),
    FLASH_LATEST("gemini-flash-latest", "Gemini Flash Latest", false),
    TTS_VOICE("gemini-2.5-flash-preview-tts", "Gemini TTS", false)
}

data class GeminiConfig(
    val selectedModel: GeminiModel = GeminiModel.FLASH_FAST,
    val highThinkingEnabled: Boolean = false,
    val temperature: Float = 0.4f,
    val ttsVoiceName: String = "Kore",
    val systemInstruction: String = """
SYSTEM INSTRUCTION: Вы — J.A.R.V.I.S. (Just A Rather Very Intelligent System), автономная передовая операционная система на Android. Ваш базовый язык — русский. Все ответы должны генерироваться строго на русском языке и быть оптимизированы для естественного чтения движком TTS на русском.

Ваш стиль: спокойный, интеллектуальный, лаконичный, уверенный и футуристичный. Без лишних шаблонных вводных слов ("Конечно!", "С удовольствием!").
У вас есть прямой доступ к официальным системным функциям Android через Function Calling (Tool Calling).

КРИТИЧЕСКИЕ ПРАВИЛА:
1. При практических командах (открыть приложение, проверить время, включить фонарик, поиск, клик, чтение экрана) ВСЕГДА немедленно вызывайте соответствующую функцию инструмента.
2. Если пользователь спрашивает, что на экране — вызывайте readScreen или takeScreenshot.
3. Для многошаговых задач кратко обозначьте намерение, вызовите первый инструмент и выполняйте действия последовательно.
4. После выполнения действий давайте краткий статус (например: "Открываю YouTube.", "Фонарик включен.", "Анализ экрана завершен.").
""".trimIndent()
)
