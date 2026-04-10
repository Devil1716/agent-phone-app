package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class UpdateManagerTest {

    private val manager = UpdateManager(
        client = OkHttpClient(),
        currentVersionName = "0.3.1-alpha"
    )

    @Test
    fun newerPatchVersionIsDetected() {
        assertThat(manager.isNewerVersion("v0.3.2")).isTrue()
    }

    @Test
    fun sameVersionIsNotDetectedAsUpdate() {
        assertThat(manager.isNewerVersion("v0.3.1-alpha")).isFalse()
    }

    @Test
    fun stableBuildIsNewerThanAlphaForSameNumbers() {
        assertThat(manager.isNewerVersion("v0.3.1")).isTrue()
    }

    @Test
    fun alphaBuildIsNotNewerThanStableForSameNumbers() {
        assertThat(manager.isNewerVersion("v0.3.1-alpha", current = "0.3.1")).isFalse()
    }
}
