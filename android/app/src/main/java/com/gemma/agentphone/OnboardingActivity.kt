package com.gemma.agentphone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gemma.agentphone.agent.ModelDownloadManager
import com.gemma.agentphone.agent.ModelDownloadStatus
import com.gemma.agentphone.model.AiSettingsRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {

    private lateinit var modelDownloadManager: ModelDownloadManager
    private val handler = Handler(Looper.getMainLooper())

    private val statusRunnable = object : Runnable {
        override fun run() {
            val keepPolling = refreshModelState()
            if (keepPolling) {
                handler.postDelayed(this, 1_000)
            }
        }
    }

    private val importModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelDownloadManager = ModelDownloadManager(this)

        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        val isCompleted = prefs.getBoolean("completed", false)
        if (isCompleted && modelDownloadManager.isModelDownloaded()) {
            launchMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        findViewById<View>(R.id.enableAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.enableNotificationButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.downloadModelButton).setOnClickListener {
            startDownload()
        }

        findViewById<MaterialButton>(R.id.importModelButton).setOnClickListener {
            importModelLauncher.launch(arrayOf("*/*"))
        }

        findViewById<MaterialButton>(R.id.openModelSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.getStartedButton).setOnClickListener {
            if (modelDownloadManager.isModelDownloaded()) {
                completeOnboarding()
            } else {
                Toast.makeText(this, R.string.onboarding_model_waiting, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.skipButton).setOnClickListener {
            completeOnboarding()
        }

        refreshModelState()
    }

    override fun onResume() {
        super.onResume()
        refreshModelState()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusRunnable)
    }

    private fun startDownload() {
        val settings = AiSettingsRepository(this).load()
        val result = modelDownloadManager.startDownload(settings.modelDownloadUrl)
        if (result.isSuccess) {
            Toast.makeText(this, R.string.model_download_started, Toast.LENGTH_SHORT).show()
            refreshModelState()
        } else {
            val messageRes = if (settings.modelDownloadUrl.isBlank()) {
                R.string.model_download_missing_url
            } else {
                R.string.model_download_failure
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = modelDownloadManager.importModel(uri)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(this@OnboardingActivity, R.string.model_import_success, Toast.LENGTH_SHORT).show()
                    refreshModelState()
                } else {
                    Toast.makeText(this@OnboardingActivity, R.string.model_import_failure, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshModelState(): Boolean {
        val downloadButton = findViewById<MaterialButton>(R.id.downloadModelButton)
        val progressContainer = findViewById<View>(R.id.downloadProgressContainer)
        val progressBar = findViewById<LinearProgressIndicator>(R.id.modelDownloadProgress)
        val progressLabel = findViewById<TextView>(R.id.modelDownloadStatus)

        return when (val status = modelDownloadManager.getStatus()) {
            is ModelDownloadStatus.Ready -> {
                handler.removeCallbacks(statusRunnable)
                progressContainer.visibility = View.GONE
                downloadButton.visibility = View.VISIBLE
                downloadButton.text = getString(R.string.onboarding_model_ready)
                downloadButton.isEnabled = false
                false
            }

            is ModelDownloadStatus.Downloading -> {
                downloadButton.visibility = View.GONE
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = status.progress
                progressLabel.text = getString(R.string.onboarding_model_downloading, status.progress)
                handler.removeCallbacks(statusRunnable)
                handler.postDelayed(statusRunnable, 1_000)
                true
            }

            is ModelDownloadStatus.Failed -> {
                handler.removeCallbacks(statusRunnable)
                progressContainer.visibility = View.GONE
                downloadButton.visibility = View.VISIBLE
                downloadButton.isEnabled = true
                downloadButton.text = getString(R.string.onboarding_model_download_button)
                false
            }

            ModelDownloadStatus.Missing -> {
                handler.removeCallbacks(statusRunnable)
                progressContainer.visibility = View.GONE
                downloadButton.visibility = View.VISIBLE
                downloadButton.isEnabled = true
                downloadButton.text = getString(R.string.onboarding_model_download_button)
                false
            }
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
