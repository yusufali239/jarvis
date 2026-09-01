package com.example.core.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Gemini Live Bidi WebSocket Data Contracts
 */

@JsonClass(generateAdapter = true)
data class LiveClientMessage(
    @Json(name = "setup") val setup: LiveSetupConfig? = null,
    @Json(name = "realtimeInput") val realtimeInput: LiveRealtimeInput? = null,
    @Json(name = "toolResponse") val toolResponse: LiveToolResponse? = null
)

@JsonClass(generateAdapter = true)
data class LiveSetupConfig(
    @Json(name = "model") val model: String = "models/gemini-2.0-flash-exp",
    @Json(name = "generationConfig") val generationConfig: LiveGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "tools") val tools: List<GeminiToolDeclarationWrapper>? = null
)

@JsonClass(generateAdapter = true)
data class LiveGenerationConfig(
    @Json(name = "responseModalities") val responseModalities: List<String> = listOf("AUDIO", "TEXT"),
    @Json(name = "speechConfig") val speechConfig: GeminiSpeechConfig? = null,
    @Json(name = "temperature") val temperature: Float? = 0.5f
)

@JsonClass(generateAdapter = true)
data class LiveRealtimeInput(
    @Json(name = "mediaChunks") val mediaChunks: List<LiveMediaChunk>
)

@JsonClass(generateAdapter = true)
data class LiveMediaChunk(
    @Json(name = "mimeType") val mimeType: String = "audio/pcm;rate=16000",
    @Json(name = "data") val data: String // Base64 encoded PCM bytes
)

@JsonClass(generateAdapter = true)
data class LiveToolResponse(
    @Json(name = "functionResponses") val functionResponses: List<LiveFunctionResponseItem>
)

@JsonClass(generateAdapter = true)
data class LiveFunctionResponseItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "response") val response: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class LiveServerMessage(
    @Json(name = "serverContent") val serverContent: LiveServerContent? = null,
    @Json(name = "toolCall") val toolCall: LiveToolCall? = null,
    @Json(name = "toolCallCancellation") val toolCallCancellation: LiveToolCallCancellation? = null
)

@JsonClass(generateAdapter = true)
data class LiveServerContent(
    @Json(name = "modelTurn") val modelTurn: GeminiContent? = null,
    @Json(name = "turnComplete") val turnComplete: Boolean? = null,
    @Json(name = "interrupted") val interrupted: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class LiveToolCall(
    @Json(name = "functionCalls") val functionCalls: List<LiveFunctionCallItem>
)

@JsonClass(generateAdapter = true)
data class LiveFunctionCallItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "args") val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class LiveToolCallCancellation(
    @Json(name = "ids") val ids: List<String>
)
