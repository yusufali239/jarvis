package com.example.core.ai

enum class TtsLanguageSupportStatus(val label: String) {
    AVAILABLE("AVAILABLE"),
    MISSING_DATA("MISSING VOICE DATA"),
    NOT_SUPPORTED("NOT SUPPORTED"),
    UNKNOWN("CHECKING...")
}

data class TtsEngineInfo(
    val name: String,
    val packageName: String,
    val isDefault: Boolean,
    val isCurrent: Boolean
)

data class TtsVoiceInfo(
    val name: String,
    val localeTag: String,
    val quality: String,
    val isNetwork: Boolean,
    val isSelected: Boolean
)

data class TtsDiagnosticState(
    val activeEngineName: String = "Detecting...",
    val activeEnginePackage: String = "",
    val installedEngines: List<TtsEngineInfo> = emptyList(),
    val isRussianSupported: Boolean = false,
    val russianSupportStatus: TtsLanguageSupportStatus = TtsLanguageSupportStatus.UNKNOWN,
    val activeVoiceName: String = "Default",
    val activeLocale: String = "ru-RU",
    val isReady: Boolean = false,
    val statusSummary: String = "INITIALIZING",
    val availableVoices: List<TtsVoiceInfo> = emptyList(),
    val diagnosticMessage: String = ""
)

enum class TtsEnginePreference(val label: String, val packageMatch: String?) {
    AUTO_BEST("Auto (Recommended)", null),
    GOOGLE_TTS("Google Speech Services", "com.google.android.tts"),
    SAMSUNG_TTS("Samsung Text-to-Speech", "com.samsung.SMT"),
    SYSTEM_DEFAULT("System Default Engine", "system_default")
}

enum class TtsLanguagePreference(val label: String, val detectedLanguage: DetectedLanguage?) {
    AUTO_DETECT("Auto Language Detection", null),
    FORCE_RUSSIAN("Force Russian (ru-RU)", DetectedLanguage.RUSSIAN),
    FORCE_ENGLISH("Force English (en-US)", DetectedLanguage.ENGLISH),
    FORCE_UZBEK("Force Uzbek (uz-UZ)", DetectedLanguage.UZBEK)
}
