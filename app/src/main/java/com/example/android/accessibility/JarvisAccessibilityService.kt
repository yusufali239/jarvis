package com.example.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class UiElementInfo(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val isClickable: Boolean,
    val bounds: Rect,
    val isEditable: Boolean
)

class JarvisAccessibilityService : AccessibilityService() {

    private val tag = "JarvisA11yService"

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(tag, "Jarvis Accessibility Service connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events received
    }

    override fun onInterrupt() {
        Log.w(tag, "Jarvis Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Traverses the current window accessibility node tree and returns structured textual UI description.
     */
    fun dumpScreenHierarchy(): List<UiElementInfo> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElementInfo>()
        traverseNode(rootNode, elements)
        return elements
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, list: MutableList<UiElementInfo>) {
        if (node == null) return

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName
        val className = node.className?.toString()
        val isClickable = node.isClickable
        val isEditable = node.isEditable

        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || isClickable || isEditable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            list.add(
                UiElementInfo(
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = className,
                    isClickable = isClickable,
                    bounds = bounds,
                    isEditable = isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, list)
        }
    }

    /**
     * Finds an element by text, description or viewId and performs a click.
     */
    fun clickElement(targetTextOrId: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // 1. Search by text
        val nodesByText = root.findAccessibilityNodeInfosByText(targetTextOrId)
        for (node in nodesByText) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }

        // 2. Search by View Resource Id
        val nodesById = root.findAccessibilityNodeInfosByViewId(targetTextOrId)
        for (node in nodesById) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }

        // 3. Fallback: traverse tree to match case-insensitively or contentDescription
        val matchedNode = findMatchingNode(root, targetTextOrId.lowercase())
        if (matchedNode != null) {
            return performClickOnNodeOrParent(matchedNode)
        }

        return false
    }

    private fun findMatchingNode(node: AccessibilityNodeInfo?, targetLower: String): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains(targetLower) || desc.contains(targetLower) || viewId.contains(targetLower)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findMatchingNode(child, targetLower)
            if (result != null) return result
        }

        return null
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Finds an editable field and inputs text.
     */
    fun typeTextIntoFocusedOrTarget(textToType: String, targetFieldHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        // First attempt: currently focused editable node
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && (focused.isEditable || focused.className?.contains("EditText", ignoreCase = true) == true)) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        // Second attempt: find editable node matching hint or any first editable node
        val editableNode = findEditableNode(root, targetFieldHint?.lowercase())
        if (editableNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?, hint: String?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) {
            if (hint == null) return node
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (text.contains(hint) || desc.contains(hint)) return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditableNode(child, hint)
            if (result != null) return result
        }
        return null
    }

    /**
     * Performs scrolling (forward or backward) on scrollable container.
     */
    fun performScroll(forward: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root) ?: root
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollable.performAction(action)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun performGlobalNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun performSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
