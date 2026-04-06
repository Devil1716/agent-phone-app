package com.gemma.agentphone

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
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
        onView(withId(R.id.commandInput)).perform(replaceText("open Spotify"))
        onView(withId(R.id.runCommandButton)).perform(click())

        onView(withId(R.id.traceText)).check(matches(withText(containsString("Goal: open Spotify"))))
        onView(withId(R.id.traceText)).check(matches(withText(containsString("Execution plan prepared successfully."))))
    }

    @Test
    fun opensSettingsScreen() {
        onView(withId(R.id.openSettingsButton)).perform(click())
        onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
    }
}
