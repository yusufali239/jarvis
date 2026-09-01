package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

enum class KeyConfigStatus(val label: String) {
    CONFIGURED("CONFIGURED"),
    MISSING("MISSING"),
    INVALID("INVALID")
}

object SecretProvider {
    private const val PREFS_NAME = "jarvis_secure_vault"
    private const val KEY_OVERRIDE_GEMINI = "key_gemini_override"
    private const val KEY_OVERRIDE_GROQ = "key_groq_override"
    private const val KEY_OVERRIDE_OPENROUTER = "key_openrouter_override"
    private const val KEY_OVERRIDE_XAI = "key_xai_override"

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cleanKey(key: String?): String {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty() ||
            trimmed.startsWith("TODO") ||
            trimmed.contains("MY_GEMINI_API_KEY") ||
            trimmed.contains("YOUR_GEMINI_API_KEY") ||
            trimmed.contains("YOUR_GROQ_API_KEY") ||
            trimmed.contains("YOUR_OPENROUTER_API_KEY") ||
            trimmed.contains("YOUR_XAI_API_KEY") ||
            trimmed.equals("null", ignoreCase = true)
        ) {
            return ""
        }
        return trimmed
    }

    private fun readKeyFromBuildConfig(fieldName: String): String {
        return try {
            val field = BuildConfig::class.java.getField(fieldName)
            cleanKey(field.get(null) as? String)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readKeyFromEnv(envName: String): String {
        return try {
            cleanKey(System.getenv(envName))
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Retrieves the Gemini API key from user override, BuildConfig, or Environment.
     */
    val geminiApiKey: String
        get() {
            val userOverride = cleanKey(prefs?.getString(KEY_OVERRIDE_GEMINI, null))
            if (userOverride.isNotBlank()) return userOverride

            val fromBuildConfig = readKeyFromBuildConfig("GEMINI_API_KEY")
            if (fromBuildConfig.isNotBlank()) return fromBuildConfig

            return readKeyFromEnv("GEMINI_API_KEY")
        }

    /**
     * Retrieves the Groq API key from user override, BuildConfig, or Environment.
     */
    val groqApiKey: String
        get() {
            val userOverride = cleanKey(prefs?.getString(KEY_OVERRIDE_GROQ, null))
            if (userOverride.isNotBlank()) return userOverride

            val fromBuildConfig = readKeyFromBuildConfig("GROQ_API_KEY")
            if (fromBuildConfig.isNotBlank()) return fromBuildConfig

            return readKeyFromEnv("GROQ_API_KEY")
        }

    /**
     * Retrieves the OpenRouter API key from user override, BuildConfig, or Environment.
     */
    val openRouterApiKey: String
        get() {
            val userOverride = cleanKey(prefs?.getString(KEY_OVERRIDE_OPENROUTER, null))
            if (userOverride.isNotBlank()) return userOverride

            val fromBuildConfig = readKeyFromBuildConfig("OPENROUTER_API_KEY")
            if (fromBuildConfig.isNotBlank()) return fromBuildConfig

            return readKeyFromEnv("OPENROUTER_API_KEY")
        }

    /**
     * Retrieves the xAI / Grok API key from user override, BuildConfig, or Environment.
     */
    val xaiApiKey: String
        get() {
            val userOverride = cleanKey(prefs?.getString(KEY_OVERRIDE_XAI, null))
            if (userOverride.isNotBlank()) return userOverride

            val fromBuildConfig = readKeyFromBuildConfig("XAI_API_KEY")
            if (fromBuildConfig.isNotBlank()) return fromBuildConfig

            return readKeyFromEnv("XAI_API_KEY")
        }

    val isGeminiConfigured: Boolean
        get() = geminiApiKey.isNotBlank() && geminiApiKey.length >= 8

    val isGroqConfigured: Boolean
        get() = groqApiKey.isNotBlank() && groqApiKey.length >= 8

    val isOpenRouterConfigured: Boolean
        get() = openRouterApiKey.isNotBlank() && openRouterApiKey.length >= 8

    val isXaiConfigured: Boolean
        get() = xaiApiKey.isNotBlank() && xaiApiKey.length >= 8

    val isApiKeyConfigured: Boolean
        get() = isGeminiConfigured || isGroqConfigured || isOpenRouterConfigured || isXaiConfigured

    fun getGeminiKeyStatus(): KeyConfigStatus = if (isGeminiConfigured) KeyConfigStatus.CONFIGURED else KeyConfigStatus.MISSING
    fun getGroqKeyStatus(): KeyConfigStatus = if (isGroqConfigured) KeyConfigStatus.CONFIGURED else KeyConfigStatus.MISSING
    fun getOpenRouterKeyStatus(): KeyConfigStatus = if (isOpenRouterConfigured) KeyConfigStatus.CONFIGURED else KeyConfigStatus.MISSING
    fun getXaiKeyStatus(): KeyConfigStatus = if (isXaiConfigured) KeyConfigStatus.CONFIGURED else KeyConfigStatus.MISSING

    fun setGroqApiKey(key: String) {
        prefs?.edit()?.putString(KEY_OVERRIDE_GROQ, key.trim())?.apply()
    }

    fun setOpenRouterApiKey(key: String) {
        prefs?.edit()?.putString(KEY_OVERRIDE_OPENROUTER, key.trim())?.apply()
    }

    fun setXaiApiKey(key: String) {
        prefs?.edit()?.putString(KEY_OVERRIDE_XAI, key.trim())?.apply()
    }

    fun setGeminiApiKey(key: String) {
        prefs?.edit()?.putString(KEY_OVERRIDE_GEMINI, key.trim())?.apply()
    }
}
