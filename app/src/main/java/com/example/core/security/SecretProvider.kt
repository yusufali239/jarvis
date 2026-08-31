package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

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

    /**
     * Retrieves the Gemini API key from user override or BuildConfig.
     */
    val geminiApiKey: String
        get() {
            val userOverride = prefs?.getString(KEY_OVERRIDE_GEMINI, null)?.trim()
            if (!userOverride.isNullOrBlank()) return userOverride

            return try {
                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                val key = (field.get(null) as? String)?.trim() ?: ""
                if (key.contains("MY_GEMINI_API_KEY") || key.contains("YOUR_GEMINI_API_KEY")) "" else key
            } catch (e: Throwable) {
                ""
            }
        }

    /**
     * Retrieves the Groq API key from user override or BuildConfig.
     */
    val groqApiKey: String
        get() {
            val userOverride = prefs?.getString(KEY_OVERRIDE_GROQ, null)?.trim()
            if (!userOverride.isNullOrBlank()) return userOverride

            return try {
                val field = BuildConfig::class.java.getField("GROQ_API_KEY")
                val key = (field.get(null) as? String)?.trim() ?: ""
                if (key.contains("YOUR_GROQ_API_KEY")) "" else key
            } catch (e: Throwable) {
                ""
            }
        }

    /**
     * Retrieves the OpenRouter API key from user override or BuildConfig.
     */
    val openRouterApiKey: String
        get() {
            val userOverride = prefs?.getString(KEY_OVERRIDE_OPENROUTER, null)?.trim()
            if (!userOverride.isNullOrBlank()) return userOverride

            return try {
                val field = BuildConfig::class.java.getField("OPENROUTER_API_KEY")
                val key = (field.get(null) as? String)?.trim() ?: ""
                if (key.contains("YOUR_OPENROUTER_API_KEY")) "" else key
            } catch (e: Throwable) {
                ""
            }
        }

    /**
     * Retrieves the xAI / Grok API key from user override or BuildConfig.
     */
    val xaiApiKey: String
        get() {
            val userOverride = prefs?.getString(KEY_OVERRIDE_XAI, null)?.trim()
            if (!userOverride.isNullOrBlank()) return userOverride

            return try {
                val field = BuildConfig::class.java.getField("XAI_API_KEY")
                val key = (field.get(null) as? String)?.trim() ?: ""
                if (key.contains("YOUR_XAI_API_KEY")) "" else key
            } catch (e: Throwable) {
                ""
            }
        }

    val isGeminiConfigured: Boolean
        get() = geminiApiKey.isNotBlank() && !geminiApiKey.contains("MY_GEMINI_API_KEY") && !geminiApiKey.contains("YOUR_GEMINI_API_KEY")

    val isGroqConfigured: Boolean
        get() = groqApiKey.isNotBlank() && !groqApiKey.contains("YOUR_GROQ_API_KEY")

    val isOpenRouterConfigured: Boolean
        get() = openRouterApiKey.isNotBlank() && !openRouterApiKey.contains("YOUR_OPENROUTER_API_KEY")

    val isXaiConfigured: Boolean
        get() = xaiApiKey.isNotBlank() && !xaiApiKey.contains("YOUR_XAI_API_KEY")

    val isApiKeyConfigured: Boolean
        get() = isGeminiConfigured || isGroqConfigured || isOpenRouterConfigured || isXaiConfigured

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
