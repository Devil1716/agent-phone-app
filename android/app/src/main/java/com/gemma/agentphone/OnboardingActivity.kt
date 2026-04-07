package com.gemma.agentphone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If onboarding already completed, skip straight to MainActivity
        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        if (prefs.getBoolean("completed", false)) {
            launchMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        findViewById<android.view.View>(R.id.enableAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<android.view.View>(R.id.enableNotificationButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<android.view.View>(R.id.getStartedButton).setOnClickListener {
            completeOnboarding()
        }

        findViewById<android.view.View>(R.id.skipButton).setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        getSharedPreferences("onboarding", MODE_PRIVATE)
            .edit()
            .putBoolean("completed", true)
            .apply()
        launchMain()
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
