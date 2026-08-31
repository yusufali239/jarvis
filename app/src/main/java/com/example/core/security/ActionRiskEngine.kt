package com.example.core.security

enum class RiskLevel(val label: String, val requiresExplicitConfirmation: Boolean) {
    LOW("Low Risk", false),
    MEDIUM("Medium Risk", true),
    HIGH("High Risk", true),
    CRITICAL("Critical Risk", true)
}

data class RiskEvaluation(
    val level: RiskLevel,
    val reason: String,
    val requiresConfirmation: Boolean,
    val promptMessage: String? = null
)

object ActionRiskEngine {
    fun evaluateAction(toolName: String, parameters: Map<String, Any?>): RiskEvaluation {
        return when (toolName.lowercase()) {
            "openapp", "gettime", "getdeviceinfo", "readscreen", "readnotifications", "flashlight", "websearch" -> {
                RiskEvaluation(
                    level = RiskLevel.LOW,
                    reason = "Read-only or safe standard system action.",
                    requiresConfirmation = false
                )
            }
            "typetext", "clickelement", "scroll", "pressback", "presshome", "launchurl" -> {
                RiskEvaluation(
                    level = RiskLevel.LOW,
                    reason = "Automated UI navigation action.",
                    requiresConfirmation = false
                )
            }
            "createreminder", "clipboard", "camerascan" -> {
                RiskEvaluation(
                    level = RiskLevel.MEDIUM,
                    reason = "Modifies user calendar or accesses clipboard/camera.",
                    requiresConfirmation = false
                )
            }
            "sendmessage", "makecall", "deletedata" -> {
                val target = parameters["target"] ?: parameters["phoneNumber"] ?: "recipient"
                RiskEvaluation(
                    level = RiskLevel.HIGH,
                    reason = "Initiates external communication or data deletion.",
                    requiresConfirmation = true,
                    promptMessage = "J.A.R.V.I.S. requests authorization to execute $toolName for $target."
                )
            }
            else -> {
                RiskEvaluation(
                    level = RiskLevel.LOW,
                    reason = "Default standard tool execution.",
                    requiresConfirmation = false
                )
            }
        }
    }
}
