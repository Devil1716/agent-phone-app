package com.gemma.agentphone

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun rendersExecutionTraceForGeneralAppCommand() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<EditText>(R.id.commandInput).setText("open Spotify")
            activity.findViewById<Button>(R.id.runCommandButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        activityRule.scenario.onActivity { activity ->
            val trace = activity.findViewById<TextView>(R.id.traceText).text.toString()
            assertThat(trace).contains("Goal: open Spotify")
            assertThat(trace).contains("Execution plan prepared successfully.")
        }
    }

    @Test
    fun opensSettingsScreen() {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<Button>(R.id.openSettingsButton).performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val resumedActivity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            .firstOrNull()

        assertThat(resumedActivity).isInstanceOf(SettingsActivity::class.java)
    }
}
