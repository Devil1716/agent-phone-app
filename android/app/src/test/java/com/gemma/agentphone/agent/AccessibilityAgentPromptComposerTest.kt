package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import com.gemma.agentphone.accessibility.AccessibilityNodeSnapshot
import com.gemma.agentphone.accessibility.AccessibilitySnapshot
import com.gemma.agentphone.accessibility.ScreenBounds
import org.junit.Test

class AccessibilityAgentPromptComposerTest {
    @Test
    fun includesGoalAndFilteredTree() {
        val snapshot = AccessibilitySnapshot(
            packageName = "com.android.settings",
            activityName = "WifiSettingsActivity",
            capturedAtMillis = 123L,
            nodes = listOf(
                AccessibilityNodeSnapshot(
                    id = 3,
                    depth = 1,
                    role = "button",
                    label = "Wi-Fi",
                    hint = "",
                    viewId = "android:id/title",
                    bounds = ScreenBounds(0, 100, 400, 180),
                    enabled = true,
                    clickable = true,
                    editable = false,
                    scrollable = false,
                    longClickable = false,
                    checkable = false
                )
            ),
            promptTree = """[#3] depth=1 role=button label="Wi-Fi" viewId=android:id/title bounds=[0,100,400,180] enabled=true actions=tap"""
        )

        val prompt = AccessibilityAgentPromptComposer.compose(
            goal = "Open Wi-Fi settings",
            snapshot = snapshot,
            stepNumber = 1,
            lastAction = null,
            reflection = null
        )

        assertThat(prompt).contains("Open Wi-Fi settings")
        assertThat(prompt).contains("package=com.android.settings")
        assertThat(prompt).contains("[#3] depth=1 role=button label=\"Wi-Fi\"")
        assertThat(prompt).contains("\"action\":\"TAP|SWIPE|TYPE|LONG_PRESS|WAIT|BACK|HOME|COMPLETE\"")
    }
}
