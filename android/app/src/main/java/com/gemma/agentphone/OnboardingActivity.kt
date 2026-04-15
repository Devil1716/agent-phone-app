package com.gemma.agentphone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.GemmaModelManager
import com.gemma.agentphone.model.ModelDownloadState
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {
    private lateinit var modelManager: GemmaModelManager

    private val importModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importModel(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelManager = GemmaModelManager(this)

        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        val isCompleted = prefs.getBoolean("completed", false)
        if (isCompleted && modelManager.isModelReady()) {
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

        findViewById<MaterialButton>(R.id.openModelSourceButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DEFAULT_MODEL_SOURCE_PAGE_URL)))
        }

        findViewById<MaterialButton>(R.id.getStartedButton).setOnClickListener {
            if (modelManager.isModelReady()) {
                completeOnboarding()
            } else {
                Toast.makeText(this, R.string.onboarding_model_waiting, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.skipButton).setOnClickListener {
            completeOnboarding()
        }

        observeDownloadState()
        renderModelState(modelManager.downloadState.value)
    }

    private fun observeDownloadState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                modelManager.downloadState.collect(::renderModelState)
            }
        }
    }

    private fun startDownload() {
        val settings = AiSettingsRepository(this).load()
        lifecycleScope.launch {
            try {
                modelManager.startOrResumeDownload(
                    downloadUrl = settings.modelDownloadUrl,
                    huggingFaceToken = settings.huggingFaceToken
                )
            } catch (throwable: Throwable) {
                val message = throwable.localizedMessage ?: getString(R.string.model_download_failure)
                Toast.makeText(this@OnboardingActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            try {
                modelManager.importModel(uri)
            } catch (throwable: Throwable) {
                val message = throwable.localizedMessage ?: getString(R.string.model_import_failure)
                Toast.makeText(this@OnboardingActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderModelState(state: ModelDownloadState) {
        val downloadButton = findViewById<MaterialButton>(R.id.downloadModelButton)
        val sourceButton = findViewById<MaterialButton>(R.id.openModelSourceButton)
        val progressContainer = findViewById<View>(R.id.downloadProgressContainer)
        val progressBar = findViewById<LinearProgressIndicator>(R.id.modelDownloadProgress)
        val progressLabel = findViewById<TextView>(R.id.modelDownloadStatus)
        val helperLabel = findViewById<TextView>(R.id.modelAccessHint)
        val getStartedButton = findViewById<MaterialButton>(R.id.getStartedButton)

        progressBar.progress = state.progressPercent
        progressLabel.text = when {
            state.message.isNotBlank() -> state.message
            state.isDownloading -> getString(R.string.onboarding_model_downloading, state.progressPercent)
            state.isVerifying -> getString(R.string.model_verifying)
            else -> getString(R.string.model_status_missing)
        }

        when {
            state.isReady -> {
                progressContainer.visibility = View.GONE
                downloadButton.visibility = View.VISIBLE
                downloadButton.text = getString(R.string.onboarding_model_ready)
                downloadButton.isEnabled = false
                sourceButton.visibility = View.GONE
                helperLabel.text = getString(R.string.model_status_ready)
                getStartedButton.isEnabled = true
            }

            state.isDownloading || state.isVerifying -> {
                downloadButton.visibility = View.GONE
                progressContainer.visibility = View.VISIBLE
                sourceButton.visibility = if (state.isDownloading) View.GONE else View.VISIBLE
                helperLabel.text = getString(R.string.onboarding_model_access_hint)
                getStartedButton.isEnabled = false
            }

            else -> {
                progressContainer.visibility = if (state.message.isNotBlank()) View.VISIBLE else View.GONE
                downloadButton.visibility = View.VISIBLE
                downloadButton.isEnabled = true
                downloadButton.text = getString(R.string.onboarding_model_download_button)
                sourceButton.visibility = View.VISIBLE
                helperLabel.text = getString(R.string.onboarding_model_access_hint)
                getStartedButton.isEnabled = false
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
