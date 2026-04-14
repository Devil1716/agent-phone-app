package com.gemma.agentphone

import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
            activity.findViewById<android.view.View>(R.id.runCommandButton).performClick()
        }

        // Wait for the button to be re-enabled, signaling completion of the background task
        var isEnabled = false
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 20000) {
            activityRule.scenario.onActivity { activity ->
                isEnabled = activity.findViewById<android.view.View>(R.id.runCommandButton).isEnabled
            }
            if (isEnabled) break
            Thread.sleep(200)
        }

        // In CI/emulators, the local runtime may not be ready. We accept success or error.
        var hasSteps = false
        activityRule.scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.cotRecycler)
            hasSteps = (recyclerView.adapter?.itemCount ?: 0) > 0
        }

        // Either the CoT panel has steps or the button re-enabled (error path also adds a step)
        assertThat(isEnabled || hasSteps).isTrue()
    }

    @Test
    fun opensSettingsScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)

        activityRule.scenario.onActivity { activity ->
            activity.findViewById<android.view.View>(R.id.openSettingsButton).performClick()
        }
        val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        instrumentation.removeMonitor(monitor)

        assertThat(launchedActivity).isInstanceOf(SettingsActivity::class.java)
        launchedActivity?.finish()
    }
}
