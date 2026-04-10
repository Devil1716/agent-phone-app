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

    @Test
    fun openAppAliasResolvesKnownPackage() {
        val action = parser.parse(
            command = "open gmail",
            responseSummary = "ACTION: OPEN_APP\nQUERY: gmail"
        )

        assertThat(action).isNotNull()
        assertThat(action!!.spec.packageName).isEqualTo("com.google.android.gm")
    }

    @Test
    fun openSettingsBuildsWifiIntent() {
        val action = parser.parse(
            command = "open wifi settings",
            responseSummary = "ACTION: OPEN_SETTINGS\nQUERY: wifi"
        )

        assertThat(action).isNotNull()
        assertThat(action!!.spec.action).isEqualTo("android.settings.WIFI_SETTINGS")
    }
}
