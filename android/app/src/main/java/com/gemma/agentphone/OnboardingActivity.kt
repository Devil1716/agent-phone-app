package com.gemma.agentphone

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.agent.ModelDownloadManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var downloadManager: ModelDownloadManager
    private var activeDownloadId: Long = -1
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (activeDownloadId != -1L) {
                val progress = downloadManager.getDownloadProgress(activeDownloadId)
                updateDownloadUI(progress)
                
                if (downloadManager.isDownloadFinished(activeDownloadId)) {
                    onDownloadComplete()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloadManager = ModelDownloadManager(this)

        // If onboarding already completed AND model exists, skip
        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        val isCompleted = prefs.getBoolean("completed", false)
        if (isCompleted && downloadManager.isModelDownloaded()) {
            launchMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        val downloadBtn = findViewById<MaterialButton>(R.id.downloadModelButton)
        val progressContainer = findViewById<View>(R.id.downloadProgressContainer)
        
        if (downloadManager.isModelDownloaded()) {
            downloadBtn.text = getString(R.string.onboarding_model_ready)
            downloadBtn.isEnabled = false
        }

        downloadBtn.setOnClickListener {
            activeDownloadId = downloadManager.startDownload()
            downloadBtn.visibility = View.GONE
            progressContainer.visibility = View.VISIBLE
            handler.post(progressRunnable)
        }

        findViewById<android.view.View>(R.id.enableAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<android.view.View>(R.id.enableNotificationButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.getStartedButton).setOnClickListener {
            if (downloadManager.isModelDownloaded()) {
                completeOnboarding()
            } else {
                android.widget.Toast.makeText(this, "Please download the Gemma 4 model first", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.view.View>(R.id.skipButton).setOnClickListener {
            completeOnboarding()
        }
    }

    private fun updateDownloadUI(progress: Int) {
        findViewById<LinearProgressIndicator>(R.id.modelDownloadProgress).progress = progress
        findViewById<TextView>(R.id.modelDownloadStatus).text = getString(R.string.onboarding_model_downloading, progress)
    }

    private fun onDownloadComplete() {
        activeDownloadId = -1L
        handler.removeCallbacks(progressRunnable)
        findViewById<View>(R.id.downloadProgressContainer).visibility = View.GONE
        val downloadBtn = findViewById<MaterialButton>(R.id.downloadModelButton)
        downloadBtn.visibility = View.VISIBLE
        downloadBtn.text = getString(R.string.onboarding_model_ready)
        downloadBtn.isEnabled = false
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
    }
}
