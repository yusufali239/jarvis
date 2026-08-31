package com.example.domain.tools

import com.example.core.ai.GeminiFunctionDeclaration
import com.example.core.ai.GeminiPropertySchema
import com.example.core.ai.GeminiToolParameters
import com.example.core.security.RiskLevel

data class ToolExecutionResult(
    val isSuccess: Boolean,
    val summary: String,
    val rawOutput: Map<String, Any?> = emptyMap()
)

interface JarvisTool {
    val name: String
    val description: String
    val riskLevel: RiskLevel
    val parameters: Map<String, ToolParamSpec>
    val requiredParameters: List<String>

    suspend fun execute(args: Map<String, Any?>): ToolExecutionResult

    fun toGeminiDeclaration(): GeminiFunctionDeclaration {
        val properties = parameters.mapValues { (_, spec) ->
            GeminiPropertySchema(
                type = spec.type,
                description = spec.description
            )
        }
        return GeminiFunctionDeclaration(
            name = name,
            description = description,
            parameters = GeminiToolParameters(
                type = "OBJECT",
                properties = properties,
                required = requiredParameters
            )
        )
    }
}

data class ToolParamSpec(
    val type: String, // "STRING", "INTEGER", "BOOLEAN", etc.
    val description: String
)
