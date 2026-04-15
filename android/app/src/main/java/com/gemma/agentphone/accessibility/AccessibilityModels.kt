package com.gemma.agentphone.accessibility

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int
        get() = (left + right) / 2

    val centerY: Int
        get() = (top + bottom) / 2

    fun promptValue(): String = "[$left,$top,$right,$bottom]"
}

data class AccessibilityNodeSnapshot(
    val id: Int,
    val depth: Int,
    val role: String,
    val label: String,
    val hint: String,
    val viewId: String,
    val bounds: ScreenBounds,
    val enabled: Boolean,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val longClickable: Boolean,
    val checkable: Boolean
) {
    fun promptLine(): String {
        val actions = buildList {
            if (clickable) add("tap")
            if (editable) add("type")
            if (scrollable) add("scroll")
            if (longClickable) add("long_press")
            if (checkable) add("toggle")
        }.ifEmpty { listOf("inspect") }.joinToString(",")

        return buildString {
            append("[#")
            append(id)
            append("] depth=")
            append(depth)
            append(" role=")
            append(role)
            if (label.isNotBlank()) {
                append(" label=\"")
                append(label)
                append('"')
            }
            if (hint.isNotBlank()) {
                append(" hint=\"")
                append(hint)
                append('"')
            }
            if (viewId.isNotBlank()) {
                append(" viewId=")
                append(viewId)
            }
            append(" bounds=")
            append(bounds.promptValue())
            append(" enabled=")
            append(enabled)
            append(" actions=")
            append(actions)
        }
    }
}

data class AccessibilitySnapshot(
    val packageName: String,
    val activityName: String,
    val capturedAtMillis: Long,
    val nodes: List<AccessibilityNodeSnapshot>,
    val promptTree: String
) {
    fun findNode(nodeId: Int): AccessibilityNodeSnapshot? = nodes.firstOrNull { it.id == nodeId }

    fun preview(maxLines: Int = 6): String {
        return nodes.take(maxLines).joinToString(separator = "\n") { it.promptLine() }
    }
}
