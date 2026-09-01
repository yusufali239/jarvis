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

class OpenRouterProvider : AIProvider {
    override val providerType: AIProviderType = AIProviderType.OPENROUTER
    override val displayName: String = "OpenRouter (Universal Gateway)"

    private val tag = "OpenRouterProvider"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val availableModels = listOf(
        AIModelInfo(
            id = "anthropic/claude-3.5-sonnet",
            name = "Claude 3.5 Sonnet",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "200k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = true,
            description = "Top-tier reasoning, code execution, and vision understanding."
        ),
        AIModelInfo(
            id = "openai/gpt-4o",
            name = "OpenAI GPT-4o",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Flagship multimodal intelligence from OpenAI."
        ),
        AIModelInfo(
            id = "deepseek/deepseek-chat",
            name = "DeepSeek V3",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "64k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High efficiency reasoning and programming model."
        ),
        AIModelInfo(
            id = "meta-llama/llama-3.3-70b-instruct",
            name = "Llama 3.3 70B Instruct",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High precision open-weights model hosted on OpenRouter."
        ),
        AIModelInfo(
            id = "google/gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "1M tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.VISION,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High-speed Gemini model routed via OpenRouter."
        ),
        AIModelInfo(
            id = "x-ai/grok-2-1212",
            name = "Grok 2 (via OpenRouter)",
            provider = AIProviderType.OPENROUTER,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "xAI Grok frontier reasoning routed via OpenRouter."
        )
    )

    override suspend fun getAvailableModels(): List<AIModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.openRouterApiKey
        if (apiKey.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val data = JSONObject(body).optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val dynamicList = mutableListOf<AIModelInfo>()
                        for (i in 0 until data.length().coerceAtMost(60)) {
                            val obj = data.getJSONObject(i)
                            val id = obj.optString("id")
                            val name = obj.optString("name", id)
                            if (id.isNotBlank()) {
                                dynamicList.add(
                                    AIModelInfo(
                                        id = id,
                                        name = name,
                                        provider = AIProviderType.OPENROUTER,
                                        contextWindow = "${obj.optInt("context_length", 128000) / 1000}k tokens",
                                        capabilities = setOf(AICapability.TEXT, AICapability.TOOL_CALLING, AICapability.STREAMING),
                                        isRecommended = id.contains("claude-3.5-sonnet") || id.contains("gpt-4o"),
                                        description = obj.optString("description", "OpenRouter model: $name")
                                    )
                                )
                            }
                        }
                        if (dynamicList.isNotEmpty()) return@withContext dynamicList
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch dynamic OpenRouter models: ${e.message}")
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
        val apiKey = SecretProvider.openRouterApiKey
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error(
                "OpenRouter API Key is not configured. Please add OPENROUTER_API_KEY in Settings or Secrets."
            )
        }

        val candidateModels = listOfNotNull(
            modelId.takeIf { it.isNotBlank() },
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "meta-llama/llama-3.1-8b-instruct:free",
            "deepseek/deepseek-chat",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "google/gemini-2.5-flash"
        ).distinct()

        var lastErrorMsg = "Unknown error"
        var lastCode = 0

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
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://ai.studio")
                    .addHeader("X-Title", "JARVIS-Android")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val code = response.code
                lastCode = code

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "HTTP $code: $responseBody"
                    } catch (e: Exception) {
                        "HTTP $code error"
                    }
                    Log.e(tag, "OpenRouter API error on $currModel ($code): $errorMsg")
                    lastErrorMsg = errorMsg

                    if (code == 404 || errorMsg.contains("No endpoints found", ignoreCase = true) || errorMsg.contains("not found", ignoreCase = true) || errorMsg.contains("No available model", ignoreCase = true)) {
                        continue // Attempt next OpenRouter candidate model
                    }

                    if (code == 429) {
                        return@withContext GeminiResult.Error(
                            message = "OpenRouter Rate Limit ($code): $errorMsg",
                            throwable = RuntimeException("HTTP 429"),
                            statusCode = 429,
                            isRateLimit = true
                        )
                    }

                    return@withContext GeminiResult.Error(
                        message = "OpenRouter Error ($code): $errorMsg",
                        throwable = RuntimeException("HTTP $code: $errorMsg"),
                        statusCode = code,
                        isRateLimit = false
                    )
                }

                val respJson = JSONObject(responseBody)
                val choices = respJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    continue
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
                    respondingProvider = "OpenRouter ($currModel)"
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception during OpenRouter model $currModel: ${e.message}", e)
                lastErrorMsg = e.localizedMessage ?: "Unknown OpenRouter error"
            }
        }

        val isRateLimit = lastErrorMsg.contains("429") || lastErrorMsg.contains("quota", ignoreCase = true)
        GeminiResult.Error(
            message = "OpenRouter failed: $lastErrorMsg",
            statusCode = if (isRateLimit) 429 else (if (lastCode != 0) lastCode else null),
            isRateLimit = isRateLimit
        )
    }

    override fun isConfigured(): Boolean {
        return SecretProvider.isOpenRouterConfigured
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
