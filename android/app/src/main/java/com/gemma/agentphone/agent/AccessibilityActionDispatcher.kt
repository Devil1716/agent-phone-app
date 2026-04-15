package com.gemma.agentphone.agent

import android.os.SystemClock
import com.gemma.agentphone.accessibility.PhoneControlService

class AccessibilityActionDispatcher(
    private val serviceProvider: () -> PhoneControlService? = { PhoneControlService.instance }
) {
    fun dispatch(command: AccessibilityActionCommand): AccessibilityDispatchResult {
        val service = serviceProvider()
            ?: return AccessibilityDispatchResult(
                success = false,
                detail = "The accessibility control service is not connected."
            )

        return when (command.action) {
            AccessibilityActionType.TAP -> {
                val success = command.nodeId?.let(service::tapNode)
                    ?: service.tapByCoords(command.x!!.toFloat(), command.y!!.toFloat())
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) {
                        "Tapped ${command.nodeId?.let { "node #$it" } ?: "${command.x},${command.y}"}."
                    } else {
                        "Tap dispatch failed."
                    }
                )
            }

            AccessibilityActionType.SWIPE -> {
                val success = service.swipe(
                    startX = command.startX!!,
                    startY = command.startY!!,
                    endX = command.endX!!,
                    endY = command.endY!!,
                    durationMs = command.durationMs
                )
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) "Swipe gesture dispatched." else "Swipe gesture failed."
                )
            }

            AccessibilityActionType.TYPE -> {
                val success = service.typeIntoNode(command.nodeId, command.text)
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) "Typed text into the active field." else "Text entry failed."
                )
            }

            AccessibilityActionType.LONG_PRESS -> {
                val success = command.nodeId?.let { service.longPressNode(it, command.durationMs) }
                    ?: service.longPressAt(
                        x = command.x!!.toFloat(),
                        y = command.y!!.toFloat(),
                        durationMs = command.durationMs
                    )
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) "Long-press gesture dispatched." else "Long-press failed."
                )
            }

            AccessibilityActionType.WAIT -> {
                SystemClock.sleep(command.durationMs)
                AccessibilityDispatchResult(success = true, detail = "Waited ${command.durationMs}ms.")
            }

            AccessibilityActionType.BACK -> {
                val success = service.pressBack()
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) "Sent the back action." else "Android rejected the back action."
                )
            }

            AccessibilityActionType.HOME -> {
                val success = service.pressHome()
                AccessibilityDispatchResult(
                    success = success,
                    detail = if (success) "Returned to the launcher." else "Android rejected the home action."
                )
            }

            AccessibilityActionType.COMPLETE -> {
                AccessibilityDispatchResult(success = true, detail = "Execution completed.")
            }
        }
    }
}
