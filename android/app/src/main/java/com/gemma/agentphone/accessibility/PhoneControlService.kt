package com.gemma.agentphone.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PhoneControlService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: PhoneControlService? = null
            private set

        fun isEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabledServices.contains(context.packageName, ignoreCase = true)
        }
    }

    private val snapshotter = AccessibilityNodeSnapshotter()
    private var lastPackageName: String = ""
    private var lastActivityName: String = ""

    @Volatile
    private var latestSnapshot: AccessibilitySnapshot? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastPackageName = event?.packageName?.toString().orEmpty()
        lastActivityName = event?.className?.toString().orEmpty()
        refreshSnapshot()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun captureSnapshot(forceRefresh: Boolean = true): AccessibilitySnapshot? {
        return if (forceRefresh) {
            refreshSnapshot()
        } else {
            latestSnapshot ?: refreshSnapshot()
        }
    }

    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = text.split('|').map { it.trim() }.filter { it.isNotBlank() }
        for (candidate in candidates.ifEmpty { listOf(text) }) {
            val target = findNodeByText(root, candidate) ?: continue
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK) || performTap(target)) {
                return true
            }
        }
        return false
    }

    fun tapByCoords(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun tapNode(nodeId: Int): Boolean {
        val node = captureSnapshot()?.findNode(nodeId) ?: return false
        return tapByCoords(node.bounds.centerX.toFloat(), node.bounds.centerY.toFloat())
    }

    fun longPressNode(nodeId: Int, durationMs: Long = 500L): Boolean {
        val node = captureSnapshot()?.findNode(nodeId) ?: return false
        return longPressAt(node.bounds.centerX.toFloat(), node.bounds.centerY.toFloat(), durationMs)
    }

    fun longPressAt(x: Float, y: Float, durationMs: Long = 500L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findFocusedOrEditableNode(root) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun typeIntoNode(nodeId: Int?, text: String): Boolean {
        if (nodeId != null) {
            val target = captureSnapshot()?.findNode(nodeId) ?: return false
            tapByCoords(target.bounds.centerX.toFloat(), target.bounds.centerY.toFloat())
            SystemClock.sleep(220L)
        }
        return inputText(text)
    }

    fun scrollDown(): Boolean = performSwipe(fromY = 0.72f, toY = 0.28f)

    fun scrollUp(): Boolean = performSwipe(fromY = 0.32f, toY = 0.76f)

    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 260L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun launchApp(appName: String): Boolean {
        val trimmed = appName.trim()
        if (trimmed.isBlank()) {
            return false
        }
        val launchables = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        val match = launchables
            .mapNotNull { info ->
                val label = info.loadLabel(packageManager).toString()
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                packageName to label
            }
            .firstOrNull { (packageName, label) ->
                val normalized = trimmed.lowercase()
                label.lowercase() == normalized ||
                    label.lowercase().contains(normalized) ||
                    packageName.lowercase().contains(normalized)
            }
            ?: return false

        val launchIntent = packageManager.getLaunchIntentForPackage(match.first) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    fun getCurrentScreenText(): String {
        return captureSnapshot(forceRefresh = false)?.promptTree.orEmpty()
    }

    fun getVisibleText(): List<String> {
        return captureSnapshot(forceRefresh = false)
            ?.nodes
            ?.flatMap { listOf(it.label, it.hint) }
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
    }

    fun currentPackageName(): String {
        return captureSnapshot(forceRefresh = false)?.packageName ?: lastPackageName
    }

    fun currentActivityName(): String = captureSnapshot(forceRefresh = false)?.activityName ?: lastActivityName

    private fun refreshSnapshot(): AccessibilitySnapshot? {
        val root = rootInActiveWindow ?: return latestSnapshot
        return try {
            snapshotter.capture(
                root = root,
                packageNameHint = root.packageName?.toString().orEmpty().ifBlank { lastPackageName },
                activityNameHint = lastActivityName
            ).also { latestSnapshot = it }
        } finally {
            root.recycle()
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val target = text.trim().lowercase()
        val values = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString()
        )
        if (values.any { it.lowercase().contains(target) }) {
            return clickableAncestor(node) ?: node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeByText(child, text)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findFocusedOrEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused || node.isAccessibilityFocused || node.isEditable) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findFocusedOrEditableNode(child)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable || current.isFocusable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun performTap(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return tapByCoords(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private fun performSwipe(fromY: Float, toY: Float): Boolean {
        val display = resources.displayMetrics
        val path = Path().apply {
            moveTo(display.widthPixels / 2f, display.heightPixels * fromY)
            lineTo(display.widthPixels / 2f, display.heightPixels * toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 220L))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
