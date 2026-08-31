package com.example.domain.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.example.android.accessibility.AccessibilityToolExecutor
import com.example.android.apps.AppManager
import com.example.android.notifications.JarvisNotificationListenerService
import com.example.android.system.SystemController
import com.example.core.security.RiskLevel
import com.example.data.memory.MemoryManager
import com.example.domain.tools.JarvisTool
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolParamSpec

class OpenAppTool(
    private val appManager: AppManager
) : JarvisTool {
    override val name = "openApp"
    override val description = "Opens any installed Android application by name or package (e.g., 'YouTube', 'Telegram', 'Chrome', 'Settings')."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "appName" to ToolParamSpec("STRING", "Name or package identifier of the app to launch (e.g. 'YouTube', 'Chrome', 'Telegram').")
    )
    override val requiredParameters = listOf("appName")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val appName = args["appName"]?.toString()
            ?: return ToolExecutionResult(false, "Missing required parameter 'appName'.")

        val success = appManager.openApp(appName)
        return if (success) {
            ToolExecutionResult(true, "Successfully launched application '$appName'.")
        } else {
            ToolExecutionResult(false, "Could not locate or launch application '$appName'.")
        }
    }
}

class LaunchUrlTool(
    private val context: Context
) : JarvisTool {
    override val name = "launchUrl"
    override val description = "Opens a web URL or initiates a web search in the default Android browser."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "url" to ToolParamSpec("STRING", "Complete URL or search query to open in browser (e.g. 'https://en.wikipedia.org' or 'physics of black holes').")
    )
    override val requiredParameters = listOf("url")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val input = args["url"]?.toString()
            ?: return ToolExecutionResult(false, "Missing parameter 'url'.")

        val finalUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://www.google.com/search?q=" + Uri.encode(input)
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolExecutionResult(true, "Navigated to: $finalUrl")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to open browser: ${e.message}")
        }
    }
}

class ReadScreenTool : JarvisTool {
    override val name = "readScreen"
    override val description = "Reads and analyzes all text and interactive UI components currently displayed on the device screen."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val result = AccessibilityToolExecutor.dumpScreen()
        return ToolExecutionResult(
            isSuccess = result.isSuccess,
            summary = result.message,
            rawOutput = result.details
        )
    }
}

class ClickElementTool : JarvisTool {
    override val name = "clickElement"
    override val description = "Taps or clicks an on-screen button, icon, or UI element matching the specified text label or ID via Accessibility."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "targetText" to ToolParamSpec("STRING", "Visible text, button label, or accessibility description to tap (e.g., 'Search', 'Subscribe', 'Send').")
    )
    override val requiredParameters = listOf("targetText")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val target = args["targetText"]?.toString()
            ?: return ToolExecutionResult(false, "Missing parameter 'targetText'.")

        val result = AccessibilityToolExecutor.click(target)
        return ToolExecutionResult(result.isSuccess, result.message)
    }
}

class TypeTextTool : JarvisTool {
    override val name = "typeText"
    override val description = "Enters specified text into the active on-screen input field or text box."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "text" to ToolParamSpec("STRING", "The text to type into the focused input field."),
        "fieldHint" to ToolParamSpec("STRING", "Optional hint or label of the target text field.")
    )
    override val requiredParameters = listOf("text")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val text = args["text"]?.toString()
            ?: return ToolExecutionResult(false, "Missing parameter 'text'.")
        val hint = args["fieldHint"]?.toString()

        val result = AccessibilityToolExecutor.type(text, hint)
        return ToolExecutionResult(result.isSuccess, result.message)
    }
}

class ScrollTool : JarvisTool {
    override val name = "scroll"
    override val description = "Scrolls the currently visible screen content up or down."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "forward" to ToolParamSpec("BOOLEAN", "True to scroll down/forward, false to scroll up/backward.")
    )
    override val requiredParameters = listOf("forward")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val forward = args["forward"] as? Boolean ?: true
        val result = AccessibilityToolExecutor.scroll(forward)
        return ToolExecutionResult(result.isSuccess, result.message)
    }
}

class PressBackTool : JarvisTool {
    override val name = "pressBack"
    override val description = "Performs the global Android Back navigation action."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val result = AccessibilityToolExecutor.pressBack()
        return ToolExecutionResult(result.isSuccess, result.message)
    }
}

class PressHomeTool : JarvisTool {
    override val name = "pressHome"
    override val description = "Navigates to the Android Home screen."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val result = AccessibilityToolExecutor.pressHome()
        return ToolExecutionResult(result.isSuccess, result.message)
    }
}

class GetDeviceInfoTool(
    private val systemController: SystemController
) : JarvisTool {
    override val name = "getDeviceInfo"
    override val description = "Retrieves real-time device hardware info, battery level, charging status, and network connectivity."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val info = systemController.getDeviceInfo()
        return ToolExecutionResult(true, info)
    }
}

class GetTimeTool(
    private val systemController: SystemController
) : JarvisTool {
    override val name = "getTime"
    override val description = "Returns the current local date, exact time, and timezone of the device."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val timeStr = systemController.getCurrentTimeFormatted()
        return ToolExecutionResult(true, "Current Time: $timeStr")
    }
}

class FlashlightTool(
    private val systemController: SystemController
) : JarvisTool {
    override val name = "flashlight"
    override val description = "Toggles or sets the hardware camera LED flashlight torch on or off."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "enable" to ToolParamSpec("BOOLEAN", "True to turn on, False to turn off. If omitted, toggles state.")
    )
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val enable = args["enable"] as? Boolean
        val success = systemController.toggleFlashlight(enable)
        val state = if (systemController.isFlashlightOn()) "ON" else "OFF"
        return if (success) {
            ToolExecutionResult(true, "Flashlight turned $state.")
        } else {
            ToolExecutionResult(false, "Unable to operate flashlight on this device.")
        }
    }
}

class ClipboardTool(
    private val systemController: SystemController
) : JarvisTool {
    override val name = "clipboard"
    override val description = "Reads from or writes text to the Android system clipboard."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "action" to ToolParamSpec("STRING", "Either 'read' to get clipboard text, or 'write' to copy text."),
        "text" to ToolParamSpec("STRING", "Text to copy into clipboard if action is 'write'.")
    )
    override val requiredParameters = listOf("action")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val action = args["action"]?.toString()?.lowercase() ?: "read"
        return if (action == "write") {
            val text = args["text"]?.toString() ?: ""
            val success = systemController.copyToClipboard(text)
            ToolExecutionResult(success, if (success) "Copied text to clipboard." else "Failed to copy to clipboard.")
        } else {
            val text = systemController.getClipboardText()
            if (text != null) {
                ToolExecutionResult(true, "Clipboard content: \"$text\"")
            } else {
                ToolExecutionResult(true, "Clipboard is currently empty.")
            }
        }
    }
}

class ReadNotificationsTool : JarvisTool {
    override val name = "readNotifications"
    override val description = "Reads and returns recently captured notifications and incoming alert messages."
    override val riskLevel = RiskLevel.LOW
    override val parameters = emptyMap<String, ToolParamSpec>()
    override val requiredParameters = emptyList<String>()

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val notifs = JarvisNotificationListenerService.getRecentNotifications()
        if (notifs.isEmpty()) {
            return ToolExecutionResult(true, "No recent notifications captured. (Ensure Notification Access is enabled in Permissions).")
        }
        val summary = notifs.take(10).joinToString("\n") { n ->
            "- [${n.packageName}]: ${n.title ?: "Alert"} — ${n.text ?: ""}"
        }
        return ToolExecutionResult(true, "Recent notifications (${notifs.size} total):\n$summary")
    }
}

class CreateReminderTool(
    private val context: Context
) : JarvisTool {
    override val name = "createReminder"
    override val description = "Opens Android calendar with pre-filled event title and description to schedule a reminder."
    override val riskLevel = RiskLevel.MEDIUM
    override val parameters = mapOf(
        "title" to ToolParamSpec("STRING", "Title or summary of the reminder/event."),
        "description" to ToolParamSpec("STRING", "Detailed notes or location for the event.")
    )
    override val requiredParameters = listOf("title")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val title = args["title"]?.toString() ?: "Reminder"
        val desc = args["description"]?.toString() ?: ""

        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, desc)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolExecutionResult(true, "Opened calendar event creation for \"$title\".")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not open calendar intent: ${e.message}")
        }
    }
}

class RememberFactTool(
    private val memoryManager: MemoryManager
) : JarvisTool {
    override val name = "rememberFact"
    override val description = "Saves a persistent fact or user preference into J.A.R.V.I.S. long-term memory."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "key" to ToolParamSpec("STRING", "Identifier or concept name (e.g. 'user_name', 'favorite_topic', 'project_deadline')."),
        "value" to ToolParamSpec("STRING", "The information to remember.")
    )
    override val requiredParameters = listOf("key", "value")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val key = args["key"]?.toString() ?: return ToolExecutionResult(false, "Missing 'key'.")
        val value = args["value"]?.toString() ?: return ToolExecutionResult(false, "Missing 'value'.")
        memoryManager.remember(key, value)
        return ToolExecutionResult(true, "Stored fact into memory: [$key: $value]")
    }
}

class RecallFactTool(
    private val memoryManager: MemoryManager
) : JarvisTool {
    override val name = "recallFact"
    override val description = "Searches J.A.R.V.I.S. long-term memory for previously remembered facts."
    override val riskLevel = RiskLevel.LOW
    override val parameters = mapOf(
        "query" to ToolParamSpec("STRING", "Keyword or concept name to search in memory.")
    )
    override val requiredParameters = listOf("query")

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult {
        val query = args["query"]?.toString() ?: ""
        val matches = memoryManager.searchMemory(query)
        if (matches.isEmpty()) {
            return ToolExecutionResult(true, "No memory entries found matching \"$query\".")
        }
        val formatted = matches.joinToString("\n") { "- ${it.key}: ${it.value}" }
        return ToolExecutionResult(true, "Found memories:\n$formatted")
    }
}
