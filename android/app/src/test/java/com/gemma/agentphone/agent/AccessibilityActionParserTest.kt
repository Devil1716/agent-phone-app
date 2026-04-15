package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityActionParserTest {
    private val parser = AccessibilityActionParser()

    @Test
    fun parsesTapNodeCommand() {
        val command = parser.parse(
            """{"action":"TAP","nodeId":7,"reason":"Open the visible result."}"""
        )

        assertThat(command.action).isEqualTo(AccessibilityActionType.TAP)
        assertThat(command.nodeId).isEqualTo(7)
        assertThat(command.reason).isEqualTo("Open the visible result.")
    }

    @Test
    fun parsesSwipeCoordinates() {
        val command = parser.parse(
            """{"action":"SWIPE","startX":300,"startY":1400,"endX":300,"endY":500,"durationMs":280,"reason":"Scroll the list."}"""
        )

        assertThat(command.action).isEqualTo(AccessibilityActionType.SWIPE)
        assertThat(command.startY).isEqualTo(1400)
        assertThat(command.endY).isEqualTo(500)
        assertThat(command.durationMs).isEqualTo(280L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTypeWithoutText() {
        parser.parse("""{"action":"TYPE","nodeId":4,"text":""}""")
    }
}
