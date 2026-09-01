package com.example.core.ai.provider

import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit

class GroqProvider : AIProvider {
    override val providerType: AIProviderType = AIProviderType.GROQ
    override val displayName: String = "Groq (LPU Inference)"

    private val tag = "GroqProvider"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val availableModels = listOf(
        AIModelInfo(
            id = "llama-3.3-70b-versatile",
            name = "Llama 3.3 70B Versatile",
            provider = AIProviderType.GROQ,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = true,
            description = "State-of-the-art open model on Groq LPU with fast tool calling."
        ),
        AIModelInfo(
            id = "llama-3.1-8b-instant",
            name = "Llama 3.1 8B Instant",
            provider = AIProviderType.GROQ,
            contextWindow = "128k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.TOOL_CALLING,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Ultra high-speed lightweight inference (over 700 tokens/sec)."
        ),
        AIModelInfo(
            id = "mixtral-8x7b-32768",
            name = "Mixtral 8x7B MoE",
            provider = AIProviderType.GROQ,
            contextWindow = "32k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "High efficiency Mixture-of-Experts model for general dialogue."
        ),
        AIModelInfo(
            id = "gemma2-9b-it",
            name = "Gemma 2 9B IT",
            provider = AIProviderType.GROQ,
            contextWindow = "8k tokens",
            capabilities = setOf(
                AICapability.TEXT,
                AICapability.STREAMING
            ),
            isRecommended = false,
            description = "Google's lightweight instruction-tuned open weights."
        )
    )

    override suspend fun getAvailableModels(): List<AIModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = SecretProvider.groqApiKey
        if (apiKey.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/models")
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
                            if (id.isNotBlank() && !id.contains("whisper", ignoreCase = true) && !id.contains("tts", ignoreCase = true)) {
                                dynamicList.add(
                                    AIModelInfo(
                                        id = id,
                                        name = id,
                                        provider = AIProviderType.GROQ,
                                        contextWindow = "Groq LPU",
                                        capabilities = setOf(AICapability.TEXT, AICapability.TOOL_CALLING, AICapability.STREAMING),
                                        isRecommended = id == "llama-3.3-70b-versatile",
                                        description = "Active Groq LPU model: $id"
                                    )
                                )
                            }
                        }
                        if (dynamicList.isNotEmpty()) return@withContext dynamicList
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch dynamic Groq models: ${e.message}")
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
        val apiKey = SecretProvider.groqApiKey
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error(
                "Groq API Key is not configured. Please add GROQ_API_KEY in Settings or Secrets."
            )
        }

        val candidateModels = listOfNotNull(
            modelId.takeIf { it.isNotBlank() },
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile",
            "llama3-70b-8192",
            "llama3-8b-8192",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        ).distinct()

        var lastErrorMsg = "Unknown error"
        var lastCode = 0

        for (currModel in candidateModels) {
            try {
                val rootJson = JSONObject()
                rootJson.put("model", currModel)
                rootJson.put("temperature", temperature)

                // Convert conversation history
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
                    if (textParts.isNotBlank()) {
                        val msgObj = JSONObject()
                        msgObj.put("role", role)
                        msgObj.put("content", textParts)
                        messagesArray.put(msgObj)
                    }

                    // Check function responses
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

                // Convert tools if supported
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
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
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
                    Log.e(tag, "Groq API error on $currModel ($code): $errorMsg")
                    lastErrorMsg = errorMsg

                    if (code == 404 || errorMsg.contains("does not exist", ignoreCase = true) || errorMsg.contains("do not have access", ignoreCase = true)) {
                        continue // Attempt next Groq candidate model
                    }

                    if (code == 429) {
                        return@withContext GeminiResult.Error(
                            message = "Groq Rate Limit ($code): $errorMsg",
                            throwable = RuntimeException("HTTP 429"),
                            statusCode = 429,
                            isRateLimit = true
                        )
                    }

                    return@withContext GeminiResult.Error(
                        message = "Groq Error ($code): $errorMsg",
                        throwable = RuntimeException("HTTP $code: $errorMsg"),
                        statusCode = code,
                        isRateLimit = false
                    )
                }

                // Parse response
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
                    respondingProvider = "Groq ($currModel)"
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception during Groq model $currModel: ${e.message}", e)
                lastErrorMsg = e.localizedMessage ?: "Unknown Groq error"
            }
        }

        val isRateLimit = lastErrorMsg.contains("429") || lastErrorMsg.contains("quota", ignoreCase = true)
        GeminiResult.Error(
            message = "Groq failed: $lastErrorMsg",
            statusCode = if (isRateLimit) 429 else (if (lastCode != 0) lastCode else null),
            isRateLimit = isRateLimit
        )
    }

    override fun isConfigured(): Boolean {
        return SecretProvider.isGroqConfigured
    }

    override fun supportsCapability(capability: AICapability, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: availableModels.first()
        return model.capabilities.contains(capability)
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
