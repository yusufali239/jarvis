package com.example.domain.agent

enum class LiveSessionState(val label: String, val description: String) {
    DISCONNECTED("OFFLINE", "Live session is not active"),
    CONNECTING("CONNECTING", "Connecting to Gemini Live..."),
    CONNECTED("CONNECTED", "Live session established"),
    LISTENING("LISTENING", "Listening to user audio..."),
    THINKING("THINKING", "Gemini is processing speech..."),
    SPEAKING("SPEAKING", "J.A.R.V.I.S. is speaking"),
    EXECUTING("EXECUTING", "Executing Android tool..."),
    INTERRUPTED("INTERRUPTED", "User barge-in detected"),
    RECONNECTING("RECONNECTING", "Reconnecting to live stream..."),
    ERROR("ERROR", "Live stream connection error")
}
