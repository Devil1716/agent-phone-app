package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutonomousActionParserTest {
    private val parser = AutonomousActionParser()

    @Test
    fun playStoreCommandBuildsPlayStoreSearch() {
        val action = parser.parse(
            command = "download Spotify from Play Store",
            responseSummary = "ACTION: PLAY_STORE_SEARCH\nQUERY: Spotify"
        )

        assertThat(action).isNotNull()
        assertThat(action!!.externalAction).isNotNull()
        assertThat(action.externalAction!!.spec.data).contains("market://search")
        assertThat(action.externalAction!!.spec.data).contains("Spotify")
    }

    @Test
    fun whatsappResponseBuildsWhatsAppDraft() {
        val action = parser.parse(
            command = "send hello on WhatsApp",
            responseSummary = "ACTION: WHATSAPP_MESSAGE\nTEXT: hello"
        )

        assertThat(action).isNotNull()
        assertThat(action!!.externalAction).isNotNull()
        assertThat(action.externalAction!!.spec.packageName).isEqualTo("com.whatsapp")
        assertThat(action.externalAction!!.spec.data).contains("wa.me")
    }

    @Test
    fun tapTextResponseBuildsAccessibilityCommand() {
        val action = parser.parse(
            command = "open the first result",
            responseSummary = """
                THOUGHT: The target button is already visible.
                ACTION: TAP_TEXT
                TARGET: Install
            """.trimIndent()
        )

        assertThat(action).isNotNull()
        assertThat(action!!.action).isEqualTo(AutonomousActionType.TAP_TEXT)
        assertThat(action.accessibilityCommand).isNotNull()
        assertThat(action.accessibilityCommand!!.targetText).isEqualTo("Install")
    }
}
