package com.example.domain.planner

import com.example.core.ai.GeminiFunctionCall
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolRegistry

data class TaskStep(
    val id: Int,
    val toolName: String,
    val arguments: Map<String, Any?>,
    var status: StepStatus = StepStatus.PENDING,
    var resultMessage: String? = null,
    var retryCount: Int = 0
)

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

class TaskPlanner(
    private val toolRegistry: ToolRegistry
) {
    suspend fun executeStepWithRetry(
        functionCall: GeminiFunctionCall,
        maxRetries: Int = 2
    ): ToolExecutionResult {
        var retries = 0
        var lastResult: ToolExecutionResult

        do {
            lastResult = toolRegistry.executeTool(functionCall.name, functionCall.args ?: emptyMap())
            if (lastResult.isSuccess) {
                return lastResult
            }
            retries++
        } while (retries <= maxRetries)

        return lastResult
    }
}
