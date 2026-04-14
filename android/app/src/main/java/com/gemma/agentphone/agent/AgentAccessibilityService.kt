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

        fun dispatchAutonomousAction(responseSummary: String): ExternalActionRequest? {
            val service = instance ?: return null
            val normalized = responseSummary.lowercase()

            when {
                normalized.contains("open notifications") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                }

                normalized.contains("go home") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }

                normalized.contains("back") -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }

                normalized.contains("tap search") -> {
                    service.tapNodeWithText("Search")
                }

                else -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    Handler(Looper.getMainLooper()).postDelayed({
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }, 700)
                }
            }

            service.refreshSnapshot()
            return null
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
}
