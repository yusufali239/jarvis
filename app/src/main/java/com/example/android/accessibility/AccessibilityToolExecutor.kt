package com.example.android.accessibility

data class ActionResult(
    val isSuccess: Boolean,
    val message: String,
    val details: Map<String, Any?> = emptyMap()
)

object AccessibilityToolExecutor {

    val isServiceAvailable: Boolean
        get() = JarvisAccessibilityService.isServiceRunning

    fun dumpScreen(): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled. Please enable J.A.R.V.I.S. in Accessibility Settings.")

        val elements = service.dumpScreenHierarchy()
        if (elements.isEmpty()) {
            return ActionResult(true, "Screen read successful, but no active text or interactive UI elements were found.")
        }

        val formatted = elements.take(50).mapIndexed { index, el ->
            val label = el.text ?: el.contentDescription ?: el.viewId ?: el.className ?: "Element"
            val type = if (el.isClickable) "[Button/Clickable]" else if (el.isEditable) "[TextField]" else "[Text]"
            "$index. $type: \"$label\""
        }.joinToString("\n")

        return ActionResult(
            isSuccess = true,
            message = "Found ${elements.size} UI elements on screen:\n$formatted",
            details = mapOf("elementCount" to elements.size)
        )
    }

    fun click(target: String): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled. Please enable J.A.R.V.I.S. in Accessibility Settings.")

        val success = service.clickElement(target)
        return if (success) {
            ActionResult(true, "Successfully tapped UI element: \"$target\"")
        } else {
            ActionResult(false, "Could not find clickable element matching \"$target\" on screen.")
        }
    }

    fun type(text: String, targetField: String? = null): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled. Please enable J.A.R.V.I.S. in Accessibility Settings.")

        val success = service.typeTextIntoFocusedOrTarget(text, targetField)
        return if (success) {
            ActionResult(true, "Successfully typed text into field: \"$text\"")
        } else {
            ActionResult(false, "Could not locate an active editable text field to type into.")
        }
    }

    fun scroll(forward: Boolean = true): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled.")

        val success = service.performScroll(forward)
        val dir = if (forward) "down/forward" else "up/backward"
        return if (success) {
            ActionResult(true, "Scrolled $dir successfully.")
        } else {
            ActionResult(false, "No scrollable container found on current screen.")
        }
    }

    fun pressBack(): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled.")

        val success = service.performGlobalBack()
        return ActionResult(success, if (success) "Executed Back action." else "Failed to execute Back action.")
    }

    fun pressHome(): ActionResult {
        val service = JarvisAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is not enabled.")

        val success = service.performGlobalHome()
        return ActionResult(success, if (success) "Executed Home action." else "Failed to execute Home action.")
    }
}
