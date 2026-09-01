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
            id = "grok-4.6",
            name = "Grok 4.6",
            provider = AIProviderType.GROK,
            contextWindow = "256k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = true,
            description = "Latest flagship frontier model by xAI with real-time reasoning."
        ),
        AIModelInfo(
            id = "grok-4.5",
            name = "Grok 4.5",
            provider = AIProviderType.GROK,
            contextWindow = "256k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High-performance frontier model by xAI."
        ),
        AIModelInfo(
            id = "grok-4.3",
            name = "Grok 4.3",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Versatile high-speed model by xAI."
        ),
        AIModelInfo(
            id = "grok-4.20-0309-reasoning",
            name = "Grok 4.20 Reasoning",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High-thinking deep reasoning checkpoint by xAI."
        ),
        AIModelInfo(
            id = "grok-4.20-0309-non-reasoning",
            name = "Grok 4.20 Non-Reasoning",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Ultra-fast direct response model by xAI."
        ),
        AIModelInfo(
            id = "grok-4.20-multi-agent",
            name = "Grok 4.20 Multi-Agent",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Specialized agentic orchestration model by xAI."
        ),
        AIModelInfo(
            id = "grok-build-0.1",
            name = "Grok Build 0.1",
            provider = AIProviderType.GROK,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Tuned for agentic software workflows and coding."
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
            description = "Legacy Grok 2 checkpoint."
        )
    )

    override suspend fun getAvailableModels(): List<AIModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.xaiApiKey
        if (apiKey.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://api.x.ai/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val data = JSONObject(body).optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val dynamicList = mutableListOf<AIModelInfo>()
                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            val id = obj.optString("id")
                            if (id.isNotBlank() && !id.contains("embed", ignoreCase = true)) {
                                dynamicList.add(
                                    AIModelInfo(
                                        id = id,
                                        name = id,
                                        provider = AIProviderType.GROK,
                                        contextWindow = "xAI Frontier",
                                        capabilities = setOf(
                                            AICapability.TEXT,
                                            AICapability.TOOL_CALLING,
                                            AICapability.STREAMING
                                        ),
                                        isRecommended = id == "grok-4.6" || id == "grok-2-latest",
                                        description = "Live active xAI model: $id"
                                    )
                                )
                            }
                        }
                        if (dynamicList.isNotEmpty()) return@withContext dynamicList
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch dynamic xAI models: ${e.message}")
            }
        }
        availableModels
    }

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

        val requestedModel = if (image != null && (modelId.isBlank() || !modelId.contains("vision"))) {
            "grok-4.6"
        } else if (modelId.isNotBlank()) {
            modelId
        } else {
            "grok-4.6"
        }

        val candidateModels = listOfNotNull(
            requestedModel,
            "grok-2-latest",
            "grok-beta",
            "grok-2-1212",
            "grok-4.6",
            "grok-4.5",
            "grok-4.3",
            "grok-4.20-0309-reasoning",
            "grok-4.20-0309-non-reasoning",
            "grok-build-0.1"
        ).distinct()

        var lastErrorMsg = ""

        for (currModel in candidateModels) {
            try {
                val rootJson = JSONObject()
                rootJson.put("model", currModel)
                rootJson.put("temperature", temperature)

                val messagesArray = JSONArray()

                val effectiveSysPrompt = systemInstruction ?: """
SYSTEM INSTRUCTION: Вы — J.A.R.V.I.S., ваш базовый язык — русский. Все ответы должны генерироваться строго на русском языке и быть оптимизированы для естественного чтения движком TTS на русском.
Вы — интеллектуальный, лаконичный и футуристичный агент операционной системы Android. Выполняйте требуемые системные действия через инструменты Function Calling.
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

                    val textParts = content.parts?.mapNotNull { it.text }?.joinToString("\n").orEmpty()

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
                    for (part in content.parts ?: emptyList()) {
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
                if (toolsDeclaration != null && supportsCapability(AICapability.TOOL_CALLING, currModel)) {
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
                    Log.e(tag, "xAI / Grok API error on $currModel ($code): $errorMsg")
                    if ((code == 400 || code == 404) && (errorMsg.contains("Model not found", ignoreCase = true) || errorMsg.contains("invalid-argument", ignoreCase = true) || errorMsg.contains("does not exist", ignoreCase = true))) {
                        lastErrorMsg = errorMsg
                        continue // Attempt next model
                    }
                    if (code == 403) {
                        val reason = if (errorMsg.contains("credits", ignoreCase = true) || errorMsg.contains("permission-denied", ignoreCase = true) || errorMsg.contains("license", ignoreCase = true)) {
                            "На аккаунте xAI отсутствуют кредиты (пополните баланс в console.x.ai)"
                        } else {
                            errorMsg
                        }
                        return@withContext GeminiResult.Error(
                            message = "Grok 403: $reason",
                            throwable = RuntimeException("HTTP 403: $errorMsg"),
                            statusCode = 403,
                            isRateLimit = false
                        )
                    }
                    if (code == 429) {
                        return@withContext GeminiResult.Error(
                            message = "Grok 429: Превышен лимит запросов xAI ($errorMsg)",
                            throwable = RuntimeException("HTTP 429"),
                            statusCode = 429,
                            isRateLimit = true
                        )
                    }
                    return@withContext GeminiResult.Error(
                        message = "Grok Error ($code): $errorMsg",
                        throwable = RuntimeException("HTTP $code: $errorMsg"),
                        statusCode = code,
                        isRateLimit = false
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

                return@withContext GeminiResult.Success(
                    text = contentText,
                    functionCalls = functionCalls,
                    respondingProvider = "Grok ($currModel)"
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception during Grok model $currModel: ${e.message}", e)
                lastErrorMsg = e.localizedMessage ?: "Unknown Grok error"
            }
        }

        val isRateLimit = lastErrorMsg.contains("429") || lastErrorMsg.contains("quota", ignoreCase = true)
        GeminiResult.Error(
            message = "Grok failed: $lastErrorMsg",
            statusCode = if (isRateLimit) 429 else null,
            isRateLimit = isRateLimit
        )
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
