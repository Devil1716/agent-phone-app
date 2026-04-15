package com.gemma.agentphone

import android.app.Activity
import android.provider.Settings
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.gemma.agentphone.agent.IntentSpec
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class IntentDispatchTest {
    private val launchedSpecs = CopyOnWriteArrayList<IntentSpec>()

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        MainActivity.externalActionLauncher = ExternalActionLauncher { _: Activity, spec ->
            launchedSpecs += spec
        }
    }

    @After
    fun tearDown() {
        MainActivity.externalActionLauncher = DefaultExternalActionLauncher
        launchedSpecs.clear()
    }

    @Test
    fun dispatchesWifiSettingsIntent() {
        runCommand("open Wi-Fi settings")
        waitForLaunch()

        assertThat(launchedSpecs.map { it.action }).contains(Settings.ACTION_WIFI_SETTINGS)
    }

    @Test
    fun dispatchesBrowserSearchIntent() {
        runCommand("search the web for Gemma")
        waitForLaunch()

        val launch = launchedSpecs.single()
        assertThat(launch.action).isEqualTo(android.content.Intent.ACTION_VIEW)
        assertThat(launch.data).contains("https://www.google.com/search?q=Gemma")
    }

    @Test
    fun dispatchesMapsIntent() {
        runCommand("navigate to the airport")
        waitForLaunch()

        val launch = launchedSpecs.single()
        assertThat(launch.action).isEqualTo(android.content.Intent.ACTION_VIEW)
        assertThat(launch.data).isEqualTo("google.navigation:q=the+airport")
    }

    private fun runCommand(command: String) {
        activityRule.scenario.onActivity { activity ->
            activity.setCommandForTesting(command)
            activity.runCurrentCommandForTesting()
        }
    }

    private fun waitForLaunch(timeoutMs: Long = 10_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (launchedSpecs.isNotEmpty()) {
                return
            }
            Thread.sleep(150L)
        }
    }
}
