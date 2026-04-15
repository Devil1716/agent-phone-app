package com.gemma.agentphone.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityNodeSnapshotter(
    private val maxNodes: Int = 64,
    private val maxDepth: Int = 22,
    private val maxPromptCharacters: Int = 4_800
) {
    fun capture(
        root: AccessibilityNodeInfo,
        packageNameHint: String,
        activityNameHint: String
    ): AccessibilitySnapshot {
        val collected = mutableListOf<AccessibilityNodeSnapshot>()
        walkNode(node = root, depth = 0, nextId = 1, output = collected)

        val promptNodes = mutableListOf<AccessibilityNodeSnapshot>()
        var currentCharacters = 0
        for (node in collected) {
            val line = node.promptLine()
            if (promptNodes.isNotEmpty() && currentCharacters + line.length > maxPromptCharacters) {
                break
            }
            promptNodes += node
            currentCharacters += line.length + 1
        }

        return AccessibilitySnapshot(
            packageName = packageNameHint.ifBlank { "unknown" },
            activityName = activityNameHint.ifBlank { "unknown" },
            capturedAtMillis = System.currentTimeMillis(),
            nodes = promptNodes,
            promptTree = promptNodes.joinToString(separator = "\n") { it.promptLine() }
        )
    }

    private fun walkNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        nextId: Int,
        output: MutableList<AccessibilityNodeSnapshot>
    ): Int {
        if (node == null || depth > maxDepth || output.size >= maxNodes) {
            return nextId
        }

        var currentId = nextId
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val visible = node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0

        if (visible) {
            val label = compactText(
                listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString()
                )
            )
            val hint = compactText(listOfNotNull(node.hintText?.toString()))
            val viewId = compactText(listOfNotNull(node.viewIdResourceName))
            val actionable = node.isClickable || node.isEditable || node.isScrollable ||
                node.isLongClickable || node.isCheckable
            val meaningful = label.isNotBlank() || hint.isNotBlank() || viewId.isNotBlank()

            if (actionable || meaningful) {
                output += AccessibilityNodeSnapshot(
                    id = currentId,
                    depth = depth,
                    role = roleFor(node),
                    label = label,
                    hint = hint,
                    viewId = viewId,
                    bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    enabled = node.isEnabled,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    longClickable = node.isLongClickable,
                    checkable = node.isCheckable
                )
                currentId += 1
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)
            try {
                currentId = walkNode(child, depth + 1, currentId, output)
                if (output.size >= maxNodes) {
                    return currentId
                }
            } finally {
                child?.recycle()
            }
        }

        return currentId
    }

    private fun roleFor(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString().orEmpty()
        val shortName = className.substringAfterLast('.').lowercase()
        return when {
            node.isEditable || shortName.contains("edittext") -> "input"
            node.isScrollable || shortName.contains("recyclerview") || shortName.contains("scroll") -> "scroll"
            node.isClickable && (shortName.contains("button") || shortName.contains("chip")) -> "button"
            node.isClickable && shortName.contains("image") -> "image_button"
            shortName.contains("checkbox") || node.isCheckable -> "toggle"
            shortName.contains("textview") -> if (node.isClickable) "button" else "text"
            shortName.isNotBlank() -> shortName
            else -> "node"
        }
    }

    private fun compactText(values: List<String>): String {
        return values
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = " | ")
            .replace('"', '\'')
            .take(120)
    }
}
