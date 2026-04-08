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
