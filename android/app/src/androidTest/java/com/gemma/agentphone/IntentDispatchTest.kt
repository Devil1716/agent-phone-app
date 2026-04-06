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
    private val launchedActions = mutableListOf<String>()

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        MainActivity.externalActionLauncher = ExternalActionLauncher { _: Activity, spec ->
            launchedActions += spec.action
        }
    }

    @After
    fun tearDown() {
        MainActivity.externalActionLauncher = DefaultExternalActionLauncher
        launchedActions.clear()
    }

    @Test
    fun dispatchesWifiSettingsIntent() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("open Wi-Fi settings")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(launchedActions).contains(Settings.ACTION_WIFI_SETTINGS)
    }

    @Test
    fun dispatchesBrowserSearchIntent() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("search the web for Gemma")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(launchedActions).contains(android.content.Intent.ACTION_VIEW)
    }
}
