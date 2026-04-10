package com.gemma.agentphone

import android.widget.EditText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(SettingsActivity::class.java)

    @Test
    fun showsRelayEndpointInput() {
        activityRule.scenario.onActivity { activity ->
            val relayInput = activity.findViewById<EditText>(R.id.relayEndpointInput)
            assertThat(relayInput).isNotNull()
            assertThat(relayInput.isShown).isTrue()
        }
    }
}
