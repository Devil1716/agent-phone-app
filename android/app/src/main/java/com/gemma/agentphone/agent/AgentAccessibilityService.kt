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

        fun isConnected(): Boolean = instance != null

        fun dispatchAutonomousAction(responseSummary: String): Boolean {
            val service = instance ?: return false
            val normalized = responseSummary.lowercase()
            val structuredAction = responseSummary.lines()
                .firstOrNull { it.trim().startsWith("ACTION:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.uppercase()
            val textPayload = responseSummary.lines()
                .firstOrNull {
                    val trimmed = it.trim()
                    trimmed.startsWith("TEXT:", ignoreCase = true) || trimmed.startsWith("QUERY:", ignoreCase = true)
                }
                ?.substringAfter(':')
                ?.trim()
                .orEmpty()

            val handled = when {
                structuredAction == "OPEN_NOTIFICATIONS" || normalized.contains("open notifications") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                }

                structuredAction == "GO_HOME" || normalized.contains("go home") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }

                structuredAction == "GO_BACK" || normalized.contains("back") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }

                structuredAction == "TAP_TEXT" && textPayload.isNotBlank() -> {
                    service.tapNodeWithText(textPayload)
                }

                structuredAction == "SCROLL_UP" || normalized.contains("scroll up") -> {
                    service.scrollUp()
                }

                structuredAction == "SCROLL_DOWN" || normalized.contains("scroll down") -> {
                    service.scrollDown()
                }

                structuredAction == "LONG_PRESS" && textPayload.isNotBlank() -> {
                    service.longPressNodeWithText(textPayload)
                }

                else -> {
                    false
                }
            }

            if (handled) {
                service.refreshSnapshot()
            }
            return handled
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
        val root = rootInActiveWindow
        val visibleText = mutableListOf<String>()
        collectText(root, visibleText)

        latestSnapshot = ScreenObservation(
            foregroundApp = packageNameHint
                ?: root?.packageName?.toString()
                ?: packageName
                ?: "unknown",
            visibleText = visibleText.distinct().take(40),
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun collectText(node: AccessibilityNodeInfo?, sink: MutableList<String>) {
        if (node == null || sink.size >= 40) {
            return
        }

        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)

        for (index in 0 until node.childCount) {
            collectText(node.getChild(index), sink)
        }
    }

    private fun tapNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val tappable = nodes.firstOrNull { it.isClickable || it.isFocusable } ?: nodes.firstOrNull() ?: return false
        if (tappable.isClickable) {
            return tappable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        val bounds = android.graphics.Rect()
        tappable.getBoundsInScreen(bounds)
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun longPressNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val tappable = nodes.firstOrNull { it.isClickable || it.isFocusable || it.isLongClickable } ?: nodes.firstOrNull() ?: return false
        
        if (tappable.isLongClickable) {
            return tappable.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        val bounds = android.graphics.Rect()
        tappable.getBoundsInScreen(bounds)
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun scrollUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.2f
        val endY = displayMetrics.heightPixels * 0.8f
        
        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.8f
        val endY = displayMetrics.heightPixels * 0.2f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }
}
