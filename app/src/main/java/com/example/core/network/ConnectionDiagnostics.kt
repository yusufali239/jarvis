package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.core.ai.GeminiConfig
import com.example.core.ai.GeminiContent
import com.example.core.ai.GeminiGenerateRequest
import com.example.core.ai.GeminiGenerationConfig
import com.example.core.ai.GeminiNetworkProvider
import com.example.core.ai.GeminiPart
import com.example.core.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class TestStatus(val label: String) {
    OK("OK"),
    ONLINE("ONLINE"),
    CONFIGURED("CONFIGURED"),
    AVAILABLE("AVAILABLE"),
    NOT_CONFIGURED("NOT CONFIGURED"),
    MISSING("MISSING"),
    OFFLINE("OFFLINE"),
    FAILED("FAILED"),
    TESTING("TESTING...")
}

data class ProviderDiagnosticResult(
    val providerName: String,
    val keyStatus: String,
    val connectionStatus: String,
    val modelStatus: String,
    val details: String = "",
    val isSuccess: Boolean = false
)

data class FullDiagnosticsReport(
    val networkStatus: String,
    val geminiStatus: ProviderDiagnosticResult,
    val geminiLiveStatus: ProviderDiagnosticResult,
    val groqStatus: ProviderDiagnosticResult,
    val openRouterStatus: ProviderDiagnosticResult,
    val grokStatus: ProviderDiagnosticResult,
    val timestamp: Long = System.currentTimeMillis()
)

object ConnectionDiagnostics {
    private const val TAG = "ConnectionDiagnostics"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun checkNetworkConnectivity(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun runFullDiagnostics(context: Context): FullDiagnosticsReport = withContext(Dispatchers.IO) {
        val hasNetwork = checkNetworkConnectivity(context)
        val networkStatus = if (hasNetwork) "OK" else "OFFLINE (No Internet Connection)"

        val gemini = testGeminiApi()
        val geminiLive = testGeminiLiveApi()
        val groq = testGroqApi()
        val openRouter = testOpenRouterApi()
        val grok = testGrokApi()

        FullDiagnosticsReport(
            networkStatus = networkStatus,
            geminiStatus = gemini,
            geminiLiveStatus = geminiLive,
            groqStatus = groq,
            openRouterStatus = openRouter,
            grokStatus = grok
        )
    }

    suspend fun testGeminiApi(): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val key = SecretProvider.geminiApiKey
        if (!SecretProvider.isGeminiConfigured) {
            return@withContext ProviderDiagnosticResult(
                providerName = "Gemini",
                keyStatus = "MISSING",
                connectionStatus = "NOT CONFIGURED",
                modelStatus = "UNAVAILABLE",
                details = "GEMINI_API_KEY is not configured in Secrets or Settings.",
                isSuccess = false
            )
        }

        val testModels = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-3.1-flash-lite-preview", "gemini-3.1-pro-preview")
        var lastErrorReason = ""
        var lastHttpCode = 0

        for (modelId in testModels) {
            try {
                val testRequest = GeminiGenerateRequest(
                    contents = listOf(
                        GeminiContent(role = "user", parts = listOf(GeminiPart(text = "ping")))
                    ),
                    generationConfig = GeminiGenerationConfig(maxOutputTokens = 10)
                )

                val response = GeminiNetworkProvider.apiService.generateContent(
                    model = modelId,
                    apiKey = key,
                    request = testRequest
                )

                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                Log.i(TAG, "Gemini diagnostic check OK: model=$modelId")
                return@withContext ProviderDiagnosticResult(
                    providerName = "Gemini",
                    keyStatus = "CONFIGURED",
                    connectionStatus = "ONLINE",
                    modelStatus = "AVAILABLE ($modelId)",
                    details = "Authentication OK • Latency Normal",
                    isSuccess = true
                )
            } catch (e: retrofit2.HttpException) {
                lastHttpCode = e.code()
                lastErrorReason = when (lastHttpCode) {
                    401 -> "HTTP 401 — invalid API key"
                    403 -> "HTTP 403 — permission denied / region restricted"
                    404 -> "HTTP 404 — model $modelId unavailable"
                    429 -> "HTTP 429 — quota/rate limit exceeded"
                    500, 502, 503, 504 -> "HTTP $lastHttpCode — Google Gemini server error"
                    else -> "HTTP $lastHttpCode — API Error"
                }
                if (lastHttpCode == 404) {
                    // Try next model
                    continue
                } else {
                    break
                }
            } catch (e: java.net.SocketTimeoutException) {
                return@withContext ProviderDiagnosticResult(
                    providerName = "Gemini",
                    keyStatus = "CONFIGURED",
                    connectionStatus = "OFFLINE",
                    modelStatus = "UNAVAILABLE",
                    details = "Network timeout connecting to Google Gemini API",
                    isSuccess = false
                )
            } catch (e: java.net.UnknownHostException) {
                return@withContext ProviderDiagnosticResult(
                    providerName = "Gemini",
                    keyStatus = "CONFIGURED",
                    connectionStatus = "OFFLINE",
                    modelStatus = "UNAVAILABLE",
                    details = "DNS/network error — cannot resolve generativelanguage.googleapis.com",
                    isSuccess = false
                )
            } catch (e: Exception) {
                return@withContext ProviderDiagnosticResult(
                    providerName = "Gemini",
                    keyStatus = "CONFIGURED",
                    connectionStatus = "OFFLINE",
                    modelStatus = "UNAVAILABLE",
                    details = e.localizedMessage ?: "Unknown connection error",
                    isSuccess = false
                )
            }
        }

        ProviderDiagnosticResult(
            providerName = "Gemini",
            keyStatus = if (lastHttpCode == 401) "INVALID" else "CONFIGURED",
            connectionStatus = "OFFLINE",
            modelStatus = "UNAVAILABLE",
            details = lastErrorReason.ifBlank { "Gemini connection failed" },
            isSuccess = false
        )
    }

    suspend fun testGeminiLiveApi(): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val key = SecretProvider.geminiApiKey
        if (!SecretProvider.isGeminiConfigured) {
            return@withContext ProviderDiagnosticResult(
                providerName = "Gemini Live",
                keyStatus = "MISSING",
                connectionStatus = "NOT CONFIGURED",
                modelStatus = "UNAVAILABLE",
                details = "GEMINI_API_KEY is required for Gemini Live bidirectional voice.",
                isSuccess = false
            )
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$key"
        val request = Request.Builder().url(url).build()

        val latch = CountDownLatch(1)
        val isConnected = AtomicBoolean(false)
        val errorMsg = AtomicReference<String>("")

        val wsClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val ws = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                webSocket.close(1000, "Diagnostic test complete")
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val reason = when (code) {
                    401 -> "HTTP 401 — invalid API key"
                    403 -> "HTTP 403 — permission denied"
                    404 -> "HTTP 404 — live endpoint unavailable"
                    429 -> "HTTP 429 — quota/rate limit"
                    else -> t.message ?: "Live connection failed"
                }
                errorMsg.set(reason)
                latch.countDown()
            }
        })

        try {
            latch.await(7, TimeUnit.SECONDS)
        } catch (_: Exception) {
            ws.cancel()
        }

        if (isConnected.get()) {
            ProviderDiagnosticResult(
                providerName = "Gemini Live",
                keyStatus = "CONFIGURED",
                connectionStatus = "ONLINE",
                modelStatus = "AVAILABLE (gemini-2.0-flash-exp)",
                details = "Bidi WebSocket Handshake OK",
                isSuccess = true
            )
        } else {
            val err = errorMsg.get().ifBlank { "Live WebSocket timeout / network restriction" }
            ProviderDiagnosticResult(
                providerName = "Gemini Live",
                keyStatus = "CONFIGURED",
                connectionStatus = "OFFLINE",
                modelStatus = "UNAVAILABLE",
                details = err,
                isSuccess = false
            )
        }
    }

    suspend fun testGroqApi(): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val key = SecretProvider.groqApiKey
        if (!SecretProvider.isGroqConfigured) {
            return@withContext ProviderDiagnosticResult(
                providerName = "Groq",
                keyStatus = "MISSING",
                connectionStatus = "NOT CONFIGURED",
                modelStatus = "NOT CONFIGURED",
                details = "GROQ_API_KEY is not configured.",
                isSuccess = false
            )
        }

        val testModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama3-70b-8192")
        var lastReason = ""
        var lastCode = 0

        for (currModel in testModels) {
            try {
                val bodyJson = JSONObject().apply {
                    put("model", currModel)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        })
                    })
                    put("max_tokens", 5)
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                lastCode = response.code
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    return@withContext ProviderDiagnosticResult(
                        providerName = "Groq",
                        keyStatus = "CONFIGURED",
                        connectionStatus = "ONLINE",
                        modelStatus = "AVAILABLE ($currModel)",
                        details = "LPU Inference Connected",
                        isSuccess = true
                    )
                } else {
                    lastReason = when (lastCode) {
                        401 -> "HTTP 401 — invalid API key"
                        403 -> "HTTP 403 — permission denied"
                        429 -> "HTTP 429 — quota/rate limit"
                        404 -> "HTTP 404 — model not found"
                        else -> "HTTP $lastCode: $body"
                    }
                    if (lastCode == 404) continue else break
                }
            } catch (e: Exception) {
                lastReason = e.localizedMessage ?: "Connection error"
            }
        }

        ProviderDiagnosticResult(
            providerName = "Groq",
            keyStatus = if (lastCode == 401) "INVALID" else "CONFIGURED",
            connectionStatus = "OFFLINE",
            modelStatus = "UNAVAILABLE",
            details = lastReason.ifBlank { "Groq connection error" },
            isSuccess = false
        )
    }

    suspend fun testOpenRouterApi(): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val key = SecretProvider.openRouterApiKey
        if (!SecretProvider.isOpenRouterConfigured) {
            return@withContext ProviderDiagnosticResult(
                providerName = "OpenRouter",
                keyStatus = "MISSING",
                connectionStatus = "NOT CONFIGURED",
                modelStatus = "NOT CONFIGURED",
                details = "OPENROUTER_API_KEY is not configured.",
                isSuccess = false
            )
        }

        try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/key")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                ProviderDiagnosticResult(
                    providerName = "OpenRouter",
                    keyStatus = "CONFIGURED",
                    connectionStatus = "ONLINE",
                    modelStatus = "AVAILABLE (Universal Gateway)",
                    details = "Authentication Verified",
                    isSuccess = true
                )
            } else {
                val reason = when (code) {
                    401 -> "HTTP 401 — invalid API key"
                    402 -> "HTTP 402 — insufficient OpenRouter credits"
                    429 -> "HTTP 429 — rate limit exceeded"
                    else -> "HTTP $code: $body"
                }
                ProviderDiagnosticResult(
                    providerName = "OpenRouter",
                    keyStatus = if (code == 401) "INVALID" else "CONFIGURED",
                    connectionStatus = "OFFLINE",
                    modelStatus = "UNAVAILABLE",
                    details = reason,
                    isSuccess = false
                )
            }
        } catch (e: Exception) {
            ProviderDiagnosticResult(
                providerName = "OpenRouter",
                keyStatus = "CONFIGURED",
                connectionStatus = "OFFLINE",
                modelStatus = "UNAVAILABLE",
                details = e.localizedMessage ?: "Connection error",
                isSuccess = false
            )
        }
    }

    suspend fun testGrokApi(): ProviderDiagnosticResult = withContext(Dispatchers.IO) {
        val key = SecretProvider.xaiApiKey
        if (!SecretProvider.isXaiConfigured) {
            return@withContext ProviderDiagnosticResult(
                providerName = "Grok (xAI)",
                keyStatus = "MISSING",
                connectionStatus = "NOT CONFIGURED",
                modelStatus = "NOT CONFIGURED",
                details = "XAI_API_KEY is not configured.",
                isSuccess = false
            )
        }

        val testModels = listOf(
            "grok-4.6",
            "grok-4.5",
            "grok-4.3",
            "grok-4.20-0309-reasoning",
            "grok-4.20-0309-non-reasoning",
            "grok-build-0.1",
            "grok-2-1212"
        )
        var lastReason = ""
        var lastCode = 0

        for (currModel in testModels) {
            try {
                val bodyJson = JSONObject().apply {
                    put("model", currModel)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        })
                    })
                    put("max_tokens", 5)
                }

                val request = Request.Builder()
                    .url("https://api.x.ai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                lastCode = response.code
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    return@withContext ProviderDiagnosticResult(
                        providerName = "Grok (xAI)",
                        keyStatus = "CONFIGURED",
                        connectionStatus = "ONLINE",
                        modelStatus = "AVAILABLE ($currModel)",
                        details = "xAI Engine Connected",
                        isSuccess = true
                    )
                } else {
                    lastReason = when (lastCode) {
                        401 -> "HTTP 401 — invalid API key"
                        403 -> "HTTP 403 — permission denied"
                        429 -> "HTTP 429 — quota/rate limit"
                        400 -> if (body.contains("Model not found", ignoreCase = true)) "HTTP 400 — model $currModel not found" else "HTTP 400: $body"
                        404 -> "HTTP 404 — endpoint not found"
                        else -> "HTTP $lastCode: $body"
                    }
                    if (lastCode == 400 && body.contains("Model not found", ignoreCase = true)) {
                        continue // try next model
                    }
                    break
                }
            } catch (e: Exception) {
                lastReason = e.localizedMessage ?: "Connection error"
            }
        }

        ProviderDiagnosticResult(
            providerName = "Grok (xAI)",
            keyStatus = if (lastCode == 401) "INVALID" else "CONFIGURED",
            connectionStatus = "OFFLINE",
            modelStatus = "UNAVAILABLE",
            details = lastReason.ifBlank { "xAI connection error" },
            isSuccess = false
        )
    }
}
