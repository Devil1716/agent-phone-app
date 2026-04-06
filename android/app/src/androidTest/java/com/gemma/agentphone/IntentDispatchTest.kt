package com.gemma.agentphone

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.provider.Settings
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.Button
import android.widget.EditText
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class IntentDispatchTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        Intents.init()
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))
        intending(hasAction(Settings.ACTION_WIFI_SETTINGS)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun dispatchesWifiSettingsIntent() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("open Wi-Fi settings")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        intended(hasAction(Settings.ACTION_WIFI_SETTINGS))
    }

    @Test
    fun dispatchesBrowserSearchIntent() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("search the web for Gemma")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        intended(allOf(hasAction(Intent.ACTION_VIEW)))
    }
}
