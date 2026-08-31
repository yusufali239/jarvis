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

    override suspend fun getAvailableModels(): List<AIModelInfo> = availableModels

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

        val effectiveModel = if (modelId.isNotBlank()) modelId else "llama-3.3-70b-versatile"

        try {
            val rootJson = JSONObject()
            rootJson.put("model", effectiveModel)
            rootJson.put("temperature", temperature)

            // Convert conversation history
            val messagesArray = JSONArray()

            val effectiveSysPrompt = systemInstruction ?: """
You are J.A.R.V.I.S., a sophisticated, calm, intellectual, and highly capable Android AI agent.
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
                if (textParts.isNotBlank()) {
                    val msgObj = JSONObject()
                    msgObj.put("role", role)
                    msgObj.put("content", textParts)
                    messagesArray.put(msgObj)
                }

                // Check function responses
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

            // Convert tools if supported
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
                .url("https://api.groq.com/openai/v1/chat/completions")
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
                Log.e(tag, "Groq API error ($code): $errorMsg")
                return@withContext GeminiResult.Error(
                    message = "Groq Error ($code): $errorMsg",
                    throwable = RuntimeException("HTTP $code")
                )
            }

            // Parse response
            val respJson = JSONObject(responseBody)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext GeminiResult.Error("Groq returned empty response choices.")
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
            Log.e(tag, "Exception during Groq turn: ${e.message}", e)
            GeminiResult.Error("Groq failed: ${e.message}", e)
        }
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
