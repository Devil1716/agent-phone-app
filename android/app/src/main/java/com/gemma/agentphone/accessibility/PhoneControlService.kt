package com.gemma.agentphone.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
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

    private var lastPackageName: String = ""
    private var lastActivityName: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastPackageName = event?.packageName?.toString().orEmpty()
        lastActivityName = event?.className?.toString().orEmpty()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeByText(root, text) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) || performTap(target)
    }

    fun tapByCoords(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
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

    fun scrollDown(): Boolean = performSwipe(fromY = 0.72f, toY = 0.28f)

    fun scrollUp(): Boolean = performSwipe(fromY = 0.32f, toY = 0.76f)

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
                val label = info.loadLabel(packageManager)?.toString().orEmpty()
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
        return getVisibleText().joinToString("\n")
    }

    fun getVisibleText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val values = mutableListOf<String>()
        collectText(root, values)
        return values.distinct()
    }

    fun currentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString() ?: lastPackageName
    }

    fun currentActivityName(): String = lastActivityName

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

    private fun collectText(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null || output.size >= 120) {
            return
        }
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (index in 0 until node.childCount) {
            collectText(node.getChild(index), output)
        }
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
