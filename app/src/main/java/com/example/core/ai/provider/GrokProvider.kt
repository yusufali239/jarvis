package com.example.core.ai.provider

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiFunctionCall
import com.example.core.ai.GeminiResult
import com.example.core.ai.GeminiToolDeclarationWrapper
import com.example.core.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Official xAI / Grok Provider implementation.
 * Communicates directly with the official xAI API endpoint: https://api.x.ai/v1/chat/completions.
 */
class GrokProvider : AIProvider {
    override val providerType: AIProviderType = AIProviderType.GROK
    override val displayName: String = "xAI (Grok)"

    private val tag = "GrokProvider"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val availableModels = listOf(
        AIModelInfo(
            id = "grok-2-latest",
            name = "Grok 2 Latest",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = true,
            description = "Flagship frontier reasoning model by xAI with real-time knowledge."
        ),
        AIModelInfo(
            id = "grok-2-vision-1212",
            name = "Grok 2 Vision",
            provider = AIProviderType.GROK,
            contextWindow = "32k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Multimodal Grok capable of analyzing visual inputs, screenshots, and camera streams."
        ),
        AIModelInfo(
            id = "grok-2-1212",
            name = "Grok 2 (1212 Checkpoint)",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High precision reasoning checkpoint by xAI."
        ),
        AIModelInfo(
            id = "grok-beta",
            name = "Grok Beta",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Experimental release of xAI's Grok model."
        )
    )

    override suspend fun getAvailableModels(): List<AIModelInfo> = availableModels

    override suspend fun processTurn(
        conversationHistory: List<GeminiContent>,
        modelId: String,
        toolsDeclaration: List<GeminiToolDeclarationWrapper>?,
        image: Bitmap?,
        temperature: Float,
        systemInstruction: String?
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.xaiApiKey
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error(
                "xAI (Grok) API Key is not configured. Please add XAI_API_KEY in Settings or Secrets."
            )
        }

        // Auto select vision model if image is present
        val effectiveModel = if (image != null && (modelId.isBlank() || !modelId.contains("vision"))) {
            "grok-2-vision-1212"
        } else if (modelId.isNotBlank()) {
            modelId
        } else {
            "grok-2-latest"
        }

        try {
            val rootJson = JSONObject()
            rootJson.put("model", effectiveModel)
            rootJson.put("temperature", temperature)

            val messagesArray = JSONArray()

            val effectiveSysPrompt = systemInstruction ?: """
You are J.A.R.V.I.S., a witty, intellectual, calm, and highly capable Android AI agent.
Be concise, proactive, confident, and futuristic.
Never use generic filler phrases like "Sure!" or "Of course!".
Execute requested Android actions directly using available tools whenever needed.
            """.trimIndent()

            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", effectiveSysPrompt)
            messagesArray.put(systemMsg)

            for (content in conversationHistory) {
                val role = when (content.role) {
                    "model", "assistant" -> "assistant"
                    else -> "user"
                }

                val textParts = content.parts.mapNotNull { it.text }.joinToString("\n")

                if (image != null && role == "user" && content == conversationHistory.lastOrNull()) {
                    val msgObj = JSONObject()
                    msgObj.put("role", role)
                    val contentParts = JSONArray()

                    if (textParts.isNotBlank()) {
                        val textPart = JSONObject()
                        textPart.put("type", "text")
                        textPart.put("text", textParts)
                        contentParts.put(textPart)
                    }

                    val imagePart = JSONObject()
                    imagePart.put("type", "image_url")
                    val imgUrlObj = JSONObject()
                    val base64 = encodeBitmapToBase64(image)
                    imgUrlObj.put("url", "data:image/jpeg;base64,$base64")
                    imagePart.put("image_url", imgUrlObj)
                    contentParts.put(imagePart)

                    msgObj.put("content", contentParts)
                    messagesArray.put(msgObj)
                } else if (textParts.isNotBlank()) {
                    val msgObj = JSONObject()
                    msgObj.put("role", role)
                    msgObj.put("content", textParts)
                    messagesArray.put(msgObj)
                }

                // Append function response items
                for (part in content.parts) {
                    if (part.functionResponse != null) {
                        val toolResponseObj = JSONObject()
                        toolResponseObj.put("role", "tool")
                        toolResponseObj.put("name", part.functionResponse.name)
                        toolResponseObj.put("content", JSONObject(part.functionResponse.response).toString())
                        messagesArray.put(toolResponseObj)
                    }
                }
            }
            rootJson.put("messages", messagesArray)

            // Convert tools
            if (toolsDeclaration != null && supportsCapability(AICapability.TOOL_CALLING, effectiveModel)) {
                val toolsArray = JSONArray()
                for (wrapper in toolsDeclaration) {
                    for (decl in wrapper.functionDeclarations) {
                        val toolObj = JSONObject()
                        toolObj.put("type", "function")

                        val fnObj = JSONObject()
                        fnObj.put("name", decl.name)
                        fnObj.put("description", decl.description)

                        val paramsObj = JSONObject()
                        paramsObj.put("type", "object")

                        val propsObj = JSONObject()
                        for ((key, prop) in decl.parameters.properties) {
                            val p = JSONObject()
                            p.put("type", prop.type.lowercase())
                            p.put("description", prop.description)
                            propsObj.put(key, p)
                        }
                        paramsObj.put("properties", propsObj)
                        paramsObj.put("required", JSONArray(decl.parameters.required))

                        fnObj.put("parameters", paramsObj)
                        toolObj.put("function", fnObj)
                        toolsArray.put(toolObj)
                    }
                }
                if (toolsArray.length() > 0) {
                    rootJson.put("tools", toolsArray)
                    rootJson.put("tool_choice", "auto")
                }
            }

            val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.x.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val code = response.code
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "HTTP $code: $responseBody"
                } catch (e: Exception) {
                    "HTTP $code error"
                }
                Log.e(tag, "xAI / Grok API error ($code): $errorMsg")
                return@withContext GeminiResult.Error(
                    message = "Grok Error ($code): $errorMsg",
                    throwable = RuntimeException("HTTP $code")
                )
            }

            val respJson = JSONObject(responseBody)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext GeminiResult.Error("Grok returned empty response choices.")
            }

            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.optJSONObject("message")
            val contentText = messageObj?.optString("content")?.takeIf { it != "null" && it.isNotBlank() }

            val functionCalls = mutableListOf<GeminiFunctionCall>()
            val toolCalls = messageObj?.optJSONArray("tool_calls")
            if (toolCalls != null) {
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val fn = tc.optJSONObject("function")
                    if (fn != null) {
                        val fnName = fn.optString("name")
                        val fnArgsStr = fn.optString("arguments", "{}")
                        val argsMap = parseJsonStringToMap(fnArgsStr)
                        functionCalls.add(GeminiFunctionCall(name = fnName, args = argsMap))
                    }
                }
            }

            GeminiResult.Success(
                text = contentText,
                functionCalls = functionCalls
            )
        } catch (e: Exception) {
            Log.e(tag, "Exception during Grok turn: ${e.message}", e)
            GeminiResult.Error("Grok failed: ${e.message}", e)
        }
    }

    override fun isConfigured(): Boolean {
        return SecretProvider.isXaiConfigured
    }

    override fun supportsCapability(capability: AICapability, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: availableModels.first()
        return model.capabilities.contains(capability)
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun parseJsonStringToMap(jsonStr: String): Map<String, Any?> {
        return try {
            val map = mutableMapOf<String, Any?>()
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.opt(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
