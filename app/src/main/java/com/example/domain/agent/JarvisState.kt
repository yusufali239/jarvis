package com.example.domain.agent

enum class JarvisState(val label: String, val description: String) {
    IDLE("STANDBY", "System ready for commands"),
    LISTENING("LISTENING", "Receiving voice input..."),
    THINKING("THINKING", "Processing neural reasoning..."),
    PLANNING("PLANNING", "Decomposing task sequence..."),
    EXECUTING("EXECUTING", "Executing Android tool action..."),
    VERIFYING("VERIFYING", "Verifying system response..."),
    SPEAKING("SPEAKING", "Synthesizing voice response..."),
    ERROR("ALERT", "Attention required")
}

data class SystemStatus(
    val isAiOnline: Boolean = true,
    val isVoiceOnline: Boolean = true,
    val isToolsOnline: Boolean = true,
    val isMemoryOnline: Boolean = true,
    val isAccessibilityActive: Boolean = false,
    val isNotificationListenerActive: Boolean = false,
    val activeTaskDescription: String = "Ready for instructions."
)
