package com.example

import com.example.core.ai.GeminiConfig
import com.example.core.ai.GeminiModel
import com.example.core.security.ActionRiskEngine
import com.example.core.security.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCoreUnitTest {

    @Test
    fun testRiskEvaluationLowRisk() {
        val eval = ActionRiskEngine.evaluateAction("openApp", mapOf("appName" to "YouTube"))
        assertEquals(RiskLevel.LOW, eval.level)
        assertFalse(eval.requiresConfirmation)
    }

    @Test
    fun testRiskEvaluationHighRisk() {
        val eval = ActionRiskEngine.evaluateAction("sendMessage", mapOf("target" to "+123456789", "text" to "Hello"))
        assertEquals(RiskLevel.HIGH, eval.level)
        assertTrue(eval.requiresConfirmation)
    }

    @Test
    fun testDefaultGeminiConfig() {
        val config = GeminiConfig()
        assertEquals(GeminiModel.FLASH_FAST, config.selectedModel)
        assertEquals("Kore", config.ttsVoiceName)
        assertFalse(config.highThinkingEnabled)
    }
}
