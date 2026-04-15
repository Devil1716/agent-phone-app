package com.gemma.agentphone.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: AgentAccessibilityService? = null
        @Volatile
        private var latestSnapshot: ScreenObservation? = null

        fun latestObservation(): ScreenObservation? = latestSnapshot

        fun executeCommand(command: AccessibilityCommand): AccessibilityCommandResult {
            val service = instance
                ?: return AccessibilityCommandResult(false, "Accessibility service is not connected yet.")
            val result = service.handleCommand(command)
            service.refreshSnapshot()
            return result
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        refreshSnapshot(event?.packageName?.toString())
    }

    override fun onInterrupt() {
        // Required by the framework.
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    private fun refreshSnapshot(packageNameHint: String? = null) {
        val root = rootInActiveWindow ?: return
        try {
            val visibleText = mutableListOf<String>()
            collectText(root, visibleText, 0)

            latestSnapshot = ScreenObservation(
                foregroundApp = packageNameHint
                    ?: root.packageName?.toString()
                    ?: packageName
                    ?: "unknown",
                visibleText = visibleText.distinct().take(40),
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // Log or handle error to prevent crash
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun handleCommand(command: AccessibilityCommand): AccessibilityCommandResult {
        return when (command.type) {
            AccessibilityCommandType.TAP_TEXT -> {
                if (command.targetText.isBlank()) {
                    AccessibilityCommandResult(false, "No target text was provided for TAP_TEXT.")
                } else if (tapNodeWithText(command.targetText)) {
                    AccessibilityCommandResult(true, "Tapped \"${
                        command.targetText
                    }\" on the current screen.")
                } else {
                    AccessibilityCommandResult(false, "Could not find \"${
                        command.targetText
                    }\" on the current screen.")
                }
            }

            AccessibilityCommandType.LONG_PRESS_TEXT -> {
                if (command.targetText.isBlank()) {
                    AccessibilityCommandResult(false, "No target text was provided for LONG_PRESS_TEXT.")
                } else if (longPressNodeWithText(command.targetText)) {
                    AccessibilityCommandResult(true, "Long-pressed \"${
                        command.targetText
                    }\" on the current screen.")
                } else {
                    AccessibilityCommandResult(false, "Could not long-press \"${
                        command.targetText
                    }\" on the current screen.")
                }
            }

            AccessibilityCommandType.INPUT_TEXT -> {
                if (command.text.isBlank()) {
                    AccessibilityCommandResult(false, "No input text was provided.")
                } else if (setText(command.targetText, command.text)) {
                    AccessibilityCommandResult(true, "Typed text into the current field.")
                } else {
                    AccessibilityCommandResult(false, "Could not find an editable field for the requested text entry.")
                }
            }

            AccessibilityCommandType.SCROLL_DOWN -> performScroll(
                forward = true,
                successMessage = "Scrolled down.",
                failureMessage = "Could not scroll down on the current screen."
            )

            AccessibilityCommandType.SCROLL_UP -> performScroll(
                forward = false,
                successMessage = "Scrolled up.",
                failureMessage = "Could not scroll up on the current screen."
            )

            AccessibilityCommandType.BACK -> performGlobal(
                AccessibilityService.GLOBAL_ACTION_BACK,
                "Went back.",
                "Android rejected the back action."
            )

            AccessibilityCommandType.HOME -> performGlobal(
                AccessibilityService.GLOBAL_ACTION_HOME,
                "Returned to the home screen.",
                "Android rejected the home action."
            )

            AccessibilityCommandType.OPEN_NOTIFICATIONS -> performGlobal(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                "Opened the notification shade.",
                "Android rejected the notifications action."
            )

            AccessibilityCommandType.OPEN_RECENTS -> performGlobal(
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                "Opened recent apps.",
                "Android rejected the recents action."
            )
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sink: MutableList<String>, depth: Int) {
        if (node == null || sink.size >= 40 || depth > 25) {
            return
        }

        try {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)

            for (index in 0 until node.childCount) {
                val child = node.getChild(index)
                if (child != null) {
                    collectText(child, sink, depth + 1)
                    try {
                        child.recycle()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Prevent traversal errors from crashing the service
        }
    }

    private fun tapNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val tappable = nodes.firstOrNull { it.isClickable || it.isFocusable } ?: return false
        if (tappable.isClickable) {
            return tappable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        return performTapGesture(tappable)
    }

    private fun longPressNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes.firstOrNull { it.isLongClickable || it.isClickable || it.isFocusable } ?: return false
        if (target.isLongClickable) {
            return target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        return performTapGesture(target, durationMs = 500L)
    }

    private fun setText(targetText: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findEditableNode(root, targetText)
            ?: return false

        val arguments = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        if (node.isEditable && matchesTarget(node, targetText)) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findEditableNode(child, targetText)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun matchesTarget(node: AccessibilityNodeInfo, targetText: String): Boolean {
        if (targetText.isBlank()) {
            return node.isFocused || node.isAccessibilityFocused || node.isEditable
        }

        val target = targetText.lowercase()
        val texts = listOfNotNull(
            node.text?.toString(),
            node.hintText?.toString(),
            node.contentDescription?.toString()
        )
        return texts.any { it.lowercase().contains(target) }
    }

    private fun performScroll(
        forward: Boolean,
        successMessage: String,
        failureMessage: String
    ): AccessibilityCommandResult {
        val root = rootInActiveWindow ?: return AccessibilityCommandResult(false, failureMessage)
        val scroller = findScrollableNode(root)
            ?: return AccessibilityCommandResult(false, failureMessage)
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return if (scroller.performAction(action)) {
            AccessibilityCommandResult(true, successMessage)
        } else {
            AccessibilityCommandResult(false, failureMessage)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findScrollableNode(child)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun performGlobal(
        globalAction: Int,
        successMessage: String,
        failureMessage: String
    ): AccessibilityCommandResult {
        return if (performGlobalAction(globalAction)) {
            AccessibilityCommandResult(true, successMessage)
        } else {
            AccessibilityCommandResult(false, failureMessage)
        }
    }

    private fun performTapGesture(
        node: AccessibilityNodeInfo,
        durationMs: Long = 50L
    ): Boolean {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}

data class AccessibilityCommandResult(
    val success: Boolean,
    val message: String
)
