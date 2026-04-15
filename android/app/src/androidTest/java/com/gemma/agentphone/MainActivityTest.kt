package com.gemma.agentphone

import android.view.View
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
    fun launchesDashboardActivity() {
        var root: View? = null
        activityRule.scenario.onActivity { activity ->
            root = activity.findViewById(android.R.id.content)
        }

        assertThat(root).isNotNull()
        assertThat(root!!.isShown).isTrue()
    }

    @Test
    fun opensSettingsScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)

        activityRule.scenario.onActivity { activity ->
            activity.openSettingsForTesting()
        }
        val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        instrumentation.removeMonitor(monitor)

        assertThat(launchedActivity).isInstanceOf(SettingsActivity::class.java)
        launchedActivity?.finish()
    }
}
