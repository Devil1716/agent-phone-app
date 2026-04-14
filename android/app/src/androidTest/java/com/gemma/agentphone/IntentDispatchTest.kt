package com.gemma.agentphone

import android.app.Activity
import android.provider.Settings
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.Button
import android.widget.EditText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
@LargeTest
class IntentDispatchTest {
    private val launchedSpecs = mutableListOf<com.gemma.agentphone.agent.IntentSpec>()

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
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("open Wi-Fi settings")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        waitForLaunch()

        assertThat(launchedSpecs.map { it.action }).contains(Settings.ACTION_WIFI_SETTINGS)
    }

    @Test
    fun dispatchesBrowserSearchIntent() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("search the web for Gemma")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        waitForLaunch()

        assertThat(launchedSpecs.map { it.action }).contains(android.content.Intent.ACTION_VIEW)
    }

    @Test
    fun dispatchesInstalledAppLaunchForGmail() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("gmail")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        waitForLaunch()

        val launch = launchedSpecs.lastOrNull()
        assertThat(launch).isNotNull()
        assertThat(launch!!.action).isEqualTo(android.content.Intent.ACTION_MAIN)
        assertThat(launch.packageName).isEqualTo("com.google.android.gm")
    }

    private fun waitForLaunch(timeoutMs: Long = 20_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (launchedSpecs.isNotEmpty()) {
                return
            }
            Thread.sleep(500)
        }
    }
}
