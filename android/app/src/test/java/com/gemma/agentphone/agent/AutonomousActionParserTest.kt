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
        assertThat(action!!.spec.data).contains("market://search")
        assertThat(action.spec.data).contains("Spotify")
    }

    @Test
    fun whatsappResponseBuildsWhatsAppDraft() {
        val action = parser.parse(
            command = "send hello on WhatsApp",
            responseSummary = "ACTION: WHATSAPP_MESSAGE\nTEXT: hello"
        )

        assertThat(action).isNotNull()
        assertThat(action!!.spec.packageName).isEqualTo("com.whatsapp")
        assertThat(action.spec.data).contains("wa.me")
    }
}
