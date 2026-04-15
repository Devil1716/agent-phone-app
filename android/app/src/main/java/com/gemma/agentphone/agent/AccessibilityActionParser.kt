package com.gemma.agentphone.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser

class AccessibilityActionParser {
    fun parse(rawResponse: String): AccessibilityActionCommand {
        val jsonObject = extractJsonObject(rawResponse)
            ?: throw IllegalStateException("Gemma did not return a JSON action object.")

        val actionName = jsonObject.readString("action")
            ?.uppercase()
            ?: throw IllegalStateException("Gemma omitted the action field.")
        val action = runCatching { AccessibilityActionType.valueOf(actionName) }
            .getOrElse { throw IllegalStateException("Unsupported action \"$actionName\".") }

        val command = AccessibilityActionCommand(
            action = action,
            nodeId = jsonObject.readInt("nodeId"),
            x = jsonObject.readInt("x"),
            y = jsonObject.readInt("y"),
            startX = jsonObject.readInt("startX"),
            startY = jsonObject.readInt("startY"),
            endX = jsonObject.readInt("endX"),
            endY = jsonObject.readInt("endY"),
            text = jsonObject.readString("text").orEmpty(),
            durationMs = jsonObject.readLong("durationMs") ?: 650L,
            reason = jsonObject.readString("reason").orEmpty()
        )

        validate(command)
        return command
    }

    private fun extractJsonObject(rawResponse: String): JsonObject? {
        val start = rawResponse.indexOf('{')
        val end = rawResponse.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            return null
        }
        return JsonParser.parseString(rawResponse.substring(start, end + 1)).asJsonObject
    }

    private fun validate(command: AccessibilityActionCommand) {
        when (command.action) {
            AccessibilityActionType.TAP,
            AccessibilityActionType.LONG_PRESS -> {
                val hasNode = command.nodeId != null
                val hasPoint = command.x != null && command.y != null
                require(hasNode || hasPoint) {
                    "${command.action.name} requires a nodeId or screen coordinates."
                }
            }

            AccessibilityActionType.SWIPE -> {
                require(
                    command.startX != null &&
                        command.startY != null &&
                        command.endX != null &&
                        command.endY != null
                ) {
                    "SWIPE requires start and end coordinates."
                }
            }

            AccessibilityActionType.TYPE -> {
                require(command.text.isNotBlank()) { "TYPE requires non-empty text." }
            }

            AccessibilityActionType.WAIT -> {
                require(command.durationMs >= 0L) { "WAIT duration must be positive." }
            }

            AccessibilityActionType.BACK,
            AccessibilityActionType.HOME,
            AccessibilityActionType.COMPLETE -> Unit
        }
    }

    private fun JsonObject.readString(name: String): String? {
        return get(name)?.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.readInt(name: String): Int? {
        return get(name)?.takeIf { !it.isJsonNull }?.asInt
    }

    private fun JsonObject.readLong(name: String): Long? {
        return get(name)?.takeIf { !it.isJsonNull }?.asLong
    }
}
