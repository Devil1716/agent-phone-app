package com.gemma.agentphone

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.gemma.agentphone.agent.AgentRuntimeFactory
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.AgentAccessibilityService
import com.gemma.agentphone.agent.ExecutionTrace
import com.gemma.agentphone.agent.ModelDownloadManager
import com.gemma.agentphone.agent.ModelDownloadStatus
import com.gemma.agentphone.agent.MediaPipeAiProvider
import com.gemma.agentphone.agent.StepStatus
import com.gemma.agentphone.agent.TraceEntry
import com.gemma.agentphone.agent.UpdateManager
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.ExecutionHistoryEntry
import com.gemma.agentphone.model.ExecutionHistoryRepository
import com.gemma.agentphone.model.SharedPreferencesKeyValueStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val UPDATE_PREFS = "app_update"
        private const val KEY_PENDING_UPDATE_URL = "pending_update_url"
        private const val KEY_ACTIVE_UPDATE_DOWNLOAD_ID = "active_update_download_id"
        private const val KEY_AWAITING_INSTALL_PERMISSION = "awaiting_install_permission"

        @JvmStatic
        var externalActionLauncher: ExternalActionLauncher = DefaultExternalActionLauncher
    }

    private val providerRegistry = AiProviderRegistry()
    private val runtimeFactory = AgentRuntimeFactory()
    private var activeExecutionJob: Job? = null
    private lateinit var statusText: TextView
    private lateinit var traceText: TextView
    private lateinit var commandInput: EditText
    private lateinit var historyRepository: ExecutionHistoryRepository
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var systemDownloadManager: DownloadManager
    private val updatePrefs by lazy { getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE) }
    private var hasCheckedForUpdates = false
    private var activeUpdateDownloadId: Long? = null
    private var pendingUpdateFallbackUrl: String? = null
    private var awaitingInstallPermissionGrant = false
    private val modelStatusHandler = Handler(Looper.getMainLooper())
    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L || downloadId != activeUpdateDownloadId) return
            handleUpdateDownloadComplete(downloadId)
        }
    }

    private val modelStatusRunnable = object : Runnable {
        override fun run() {
            val isStillDownloading = refreshModelStatus()
            if (isStillDownloading) {
                modelStatusHandler.postDelayed(this, 1_000)
            }
        }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                commandInput.setText(spoken)
                commandInput.setSelection(spoken.length)
            }
        }
    }

    private val importModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = modelDownloadManager.importModel(uri)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, R.string.model_import_success, Toast.LENGTH_SHORT).show()
                    refreshModelStatus()
                } else {
                    Toast.makeText(this@MainActivity, R.string.model_import_failure, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyRepository = ExecutionHistoryRepository(
            SharedPreferencesKeyValueStore(
                getSharedPreferences("execution_history", MODE_PRIVATE)
            )
        )
        modelDownloadManager = ModelDownloadManager(this)
        systemDownloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        restorePendingUpdateState()
        registerUpdateDownloadReceiver()

        statusText = findViewById(R.id.statusText)
        traceText = findViewById(R.id.traceText)
        commandInput = findViewById(R.id.commandInput)
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.main_version_format, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.updateText).text = getString(R.string.update_checking)

        findViewById<android.view.View>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.runCommandButton).setOnClickListener {
            runCommand()
        }
        findViewById<android.view.View>(R.id.stopCommandButton).setOnClickListener {
            stopActiveExecution()
        }
        findViewById<android.view.View>(R.id.voiceInputButton).setOnClickListener {
            launchVoiceInput()
        }
        findViewById<android.view.View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.downloadModelButtonMain).setOnClickListener {
            startModelDownload()
        }
        findViewById<android.view.View>(R.id.importModelButtonMain).setOnClickListener {
            importModelLauncher.launch(arrayOf("*/*"))
        }
        findViewById<android.view.View>(R.id.openModelSourceButtonMain).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DEFAULT_MODEL_SOURCE_PAGE_URL)))
        }

        applyPrefillCommand(intent)
    }

    override fun onResume() {
        super.onResume()
        val settings = AiSettingsRepository(this).load()
        val orchestrator = AgentOrchestrator(
            settings = settings,
            providerRegistry = providerRegistry
        )

        statusText.text = buildString {
            appendLine("Professional alpha track: Android 12-14 app-control agent")
            appendLine()
            orchestrator.summaryLines().forEach(::appendLine)
            appendLine()
            appendLine("Accessibility control connected: ${AgentAccessibilityService.isConnected()}")
            appendLine("Available providers:")
            providerRegistry.listProviders().forEach { provider ->
                appendLine("- ${provider.displayName}: ${provider.models.joinToString()}")
            }
        }
        if (traceText.text.isNullOrBlank()) {
            traceText.text = getString(R.string.trace_idle)
        }
        refreshModelStatus()
        prewarmLocalModelIfReady()
        if (BuildConfig.ENABLE_IN_APP_UPDATES && !hasCheckedForUpdates) {
            hasCheckedForUpdates = true
            checkForUpdates()
        }
        resumePendingUpdateAfterPermissionGrant()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPrefillCommand(intent)
    }

    override fun onPause() {
        super.onPause()
        modelStatusHandler.removeCallbacks(modelStatusRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateDownloadReceiver)
    }

    private fun runCommand() {
        val command = commandInput.text.toString()
        if (command.isBlank()) return

        val runCommandButton = findViewById<android.view.View>(R.id.runCommandButton)
        activeExecutionJob?.cancel()
        traceText.text = getString(R.string.trace_analyzing)
        runCommandButton.isEnabled = false

        activeExecutionJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val settings = AiSettingsRepository(this@MainActivity).load()
                val trace = runtimeFactory.createCoordinator(this@MainActivity, settings, providerRegistry).run(command)

                withContext(Dispatchers.Main) {
                    historyRepository.add(
                        ExecutionHistoryEntry(
                            timestampMs = System.currentTimeMillis(),
                            commandText = command,
                            category = trace.goal.category.name,
                            strategy = trace.strategy.name,
                            resultSummary = trace.finalMessage,
                            awaitedConfirmation = trace.awaitingConfirmation
                        )
                    )

                    traceText.text = renderTrace(trace)

                    if (trace.awaitingConfirmation) {
                        showConfirmationDialog(trace)
                    } else {
                        trace.externalActions.forEach { externalActionLauncher.launch(this@MainActivity, it.spec) }
                    }
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) {
                    traceText.text = getString(R.string.trace_canceled)
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    traceText.text =
                        getString(
                            R.string.trace_error_with_reason,
                            exception.localizedMessage ?: getString(R.string.trace_unknown_error)
                        )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    runCommandButton.isEnabled = true
                    if (activeExecutionJob?.isCancelled != false) {
                        activeExecutionJob = null
                    }
                }
            }
        }
    }

    private fun stopActiveExecution() {
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        findViewById<android.view.View>(R.id.runCommandButton).isEnabled = true
        traceText.text = getString(R.string.trace_canceled)
    }

    private fun startModelDownload() {
        if (modelDownloadManager.isModelDownloaded()) {
            Toast.makeText(this, R.string.model_download_already_present, Toast.LENGTH_SHORT).show()
            refreshModelStatus()
            return
        }

        val settings = AiSettingsRepository(this).load()
        lifecycleScope.launch(Dispatchers.IO) {
            val validationResult = modelDownloadManager.validateDownloadSource(
                downloadUrl = settings.modelDownloadUrl,
                huggingFaceToken = settings.huggingFaceToken
            )

            withContext(Dispatchers.Main) {
                if (validationResult.isFailure) {
                    Toast.makeText(
                        this@MainActivity,
                        validationResult.exceptionOrNull()?.message ?: getString(R.string.model_download_failure),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshModelStatus()
                    return@withContext
                }

                val result = modelDownloadManager.startDownload(
                    downloadUrl = settings.modelDownloadUrl,
                    huggingFaceToken = settings.huggingFaceToken
                )
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, R.string.model_download_started, Toast.LENGTH_SHORT).show()
                    refreshModelStatus()
                } else {
                    val messageRes = when (result.exceptionOrNull()?.message) {
                        ModelDownloadManager.ERROR_MODEL_ALREADY_PRESENT -> R.string.model_download_already_present
                        ModelDownloadManager.ERROR_MODEL_DOWNLOAD_ACTIVE -> R.string.model_download_already_running
                        else -> if (settings.modelDownloadUrl.isBlank()) {
                            R.string.model_download_missing_url
                        } else {
                            R.string.model_download_failure
                        }
                    }
                    Toast.makeText(this@MainActivity, messageRes, Toast.LENGTH_SHORT).show()
                    refreshModelStatus()
                }
            }
        }
    }

    private fun refreshModelStatus(): Boolean {
        val statusTextView = findViewById<TextView>(R.id.modelStatusText)
        val progressIndicator = findViewById<LinearProgressIndicator>(R.id.modelDownloadProgressMain)
        val downloadButton = findViewById<MaterialButton>(R.id.downloadModelButtonMain)
        val importButton = findViewById<MaterialButton>(R.id.importModelButtonMain)
        val sourceButton = findViewById<MaterialButton>(R.id.openModelSourceButtonMain)

        return when (val status = modelDownloadManager.getStatus()) {
            is ModelDownloadStatus.Ready -> {
                statusTextView.text = getString(R.string.model_status_ready)
                progressIndicator.visibility = android.view.View.GONE
                downloadButton.isEnabled = false
                importButton.isEnabled = true
                sourceButton.isEnabled = false
                false
            }

            is ModelDownloadStatus.Downloading -> {
                statusTextView.text = getString(R.string.model_status_downloading, status.progress)
                progressIndicator.visibility = android.view.View.VISIBLE
                progressIndicator.progress = status.progress
                downloadButton.isEnabled = false
                importButton.isEnabled = false
                sourceButton.isEnabled = false
                modelStatusHandler.removeCallbacks(modelStatusRunnable)
                modelStatusHandler.postDelayed(modelStatusRunnable, 1_000)
                true
            }

            is ModelDownloadStatus.Failed -> {
                statusTextView.text = status.message
                progressIndicator.visibility = android.view.View.GONE
                downloadButton.isEnabled = true
                importButton.isEnabled = true
                sourceButton.isEnabled = true
                false
            }

            ModelDownloadStatus.Missing -> {
                statusTextView.text = getString(R.string.model_status_missing)
                progressIndicator.visibility = android.view.View.GONE
                downloadButton.isEnabled = true
                importButton.isEnabled = true
                sourceButton.isEnabled = true
                false
            }
        }
    }

    private fun checkForUpdates() {
        val updateManager = UpdateManager()
        val fallbackReleaseUrl = updateManager.latestReleasePageUrl()
        updateManager.checkForUpdates(
            onUpdateFound = { version, url ->
                runOnUiThread {
                    val updateCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.updateCard)
                    val updateText = findViewById<TextView>(R.id.updateText)
                    val downloadButton = findViewById<MaterialButton>(R.id.downloadUpdateButton)

                    updateText.text = getString(R.string.update_available, version)
                    downloadButton.text = if (url.endsWith(".apk", ignoreCase = true)) {
                        getString(R.string.update_action_download_apk)
                    } else {
                        getString(R.string.update_action_open_release)
                    }
                    updateCard.visibility = android.view.View.VISIBLE
                    downloadButton.setOnClickListener {
                        if (url.endsWith(".apk", ignoreCase = true)) {
                            downloadAndInstallUpdate(url)
                        } else {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    }
                }
            },
            onNoUpdate = {
                runOnUiThread {
                    val updateCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.updateCard)
                    val updateText = findViewById<TextView>(R.id.updateText)
                    val downloadButton = findViewById<MaterialButton>(R.id.downloadUpdateButton)
                    updateText.text = getString(R.string.update_up_to_date)
                    downloadButton.text = getString(R.string.update_action_open_release)
                    downloadButton.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackReleaseUrl)))
                    }
                    updateCard.visibility = android.view.View.VISIBLE
                }
            },
            onError = {
                runOnUiThread {
                    val updateCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.updateCard)
                    val updateText = findViewById<TextView>(R.id.updateText)
                    val downloadButton = findViewById<MaterialButton>(R.id.downloadUpdateButton)
                    updateText.text = getString(R.string.update_check_failed)
                    downloadButton.text = getString(R.string.update_action_open_release)
                    downloadButton.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackReleaseUrl)))
                    }
                    updateCard.visibility = android.view.View.VISIBLE
                }
            }
        )
    }

    private fun downloadAndInstallUpdate(apkUrl: String) {
        pendingUpdateFallbackUrl = apkUrl
        updatePrefs.edit().putString(KEY_PENDING_UPDATE_URL, apkUrl).apply()
        if (!packageManager.canRequestPackageInstalls()) {
            awaitingInstallPermissionGrant = true
            updatePrefs.edit().putBoolean(KEY_AWAITING_INSTALL_PERMISSION, true).apply()
            Toast.makeText(this, R.string.update_install_permission_needed, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val targetFileName = "gemma-agent-update.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(getString(R.string.update_download_title))
            .setDescription(getString(R.string.update_download_description))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(this, null, targetFileName)

        activeUpdateDownloadId = systemDownloadManager.enqueue(request)
        activeUpdateDownloadId?.let { id ->
            updatePrefs.edit().putLong(KEY_ACTIVE_UPDATE_DOWNLOAD_ID, id).apply()
        }
        updatePrefs.edit().putBoolean(KEY_AWAITING_INSTALL_PERMISSION, false).apply()
        Toast.makeText(this, R.string.update_download_started, Toast.LENGTH_SHORT).show()
    }

    private fun resumePendingUpdateAfterPermissionGrant() {
        if (!awaitingInstallPermissionGrant) return
        awaitingInstallPermissionGrant = false
        updatePrefs.edit().putBoolean(KEY_AWAITING_INSTALL_PERMISSION, false).apply()
        if (!packageManager.canRequestPackageInstalls()) {
            return
        }
        pendingUpdateFallbackUrl?.let { downloadAndInstallUpdate(it) }
    }

    private fun registerUpdateDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handleUpdateDownloadComplete(downloadId: Long) {
        val cursor = systemDownloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) {
                Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                clearPendingUpdateState()
                return
            }
            val downloadedUri = systemDownloadManager.getUriForDownloadedFile(downloadId)
            if (downloadedUri != null) {
                clearPendingUpdateState()
                launchDownloadedApkInstaller(downloadedUri)
                return
            }
            val localUriString = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (localUriString.isNullOrBlank()) {
                Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                clearPendingUpdateState()
                return
            }
            clearPendingUpdateState()
            launchDownloadedApkInstaller(Uri.parse(localUriString))
        }
    }

    private fun launchDownloadedApkInstaller(localUri: Uri) {
        val apkUri = if (localUri.scheme == "content") {
            localUri
        } else {
            val file = File(localUri.path.orEmpty())
            if (!file.exists()) {
                Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()
                return
            }
            FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            pendingUpdateFallbackUrl?.let { fallback ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
            }
        }
    }

    private fun restorePendingUpdateState() {
        pendingUpdateFallbackUrl = updatePrefs.getString(KEY_PENDING_UPDATE_URL, null)
        activeUpdateDownloadId = updatePrefs.getLong(KEY_ACTIVE_UPDATE_DOWNLOAD_ID, -1L).takeIf { it > 0L }
        awaitingInstallPermissionGrant = updatePrefs.getBoolean(KEY_AWAITING_INSTALL_PERMISSION, false)
    }

    private fun clearPendingUpdateState() {
        activeUpdateDownloadId = null
        updatePrefs.edit()
            .remove(KEY_ACTIVE_UPDATE_DOWNLOAD_ID)
            .remove(KEY_AWAITING_INSTALL_PERMISSION)
            .apply()
    }

    private fun showConfirmationDialog(trace: ExecutionTrace) {
        val pendingEntry = trace.entries.firstOrNull { it.status == StepStatus.PENDING_CONFIRMATION }
        val dialog = ConfirmationDialogFragment.newInstance(
            stepDescription = pendingEntry?.description ?: "Continue with this action?",
            stepReason = pendingEntry?.detail ?: ""
        )
        dialog.onConfirm = {
            traceText.append("\n\n[confirmed] User approved the pending action.")
            trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
        }
        dialog.onCancel = {
            traceText.append("\n\n[canceled] User canceled execution.")
        }
        dialog.show(supportFragmentManager, ConfirmationDialogFragment.TAG)
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            traceText.text = getString(R.string.trace_voice_unavailable)
        }
    }

    private fun prewarmLocalModelIfReady() {
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                val settings = AiSettingsRepository(this@MainActivity).load()
                if (settings.activeProvider == "gemma-local" &&
                    modelDownloadManager.getStatus() is ModelDownloadStatus.Ready
                ) {
                    val modelFile = modelDownloadManager.getModelFile()
                    MediaPipeAiProvider.prewarm(this@MainActivity, modelFile)
                }
            }
        }
    }

    private fun applyPrefillCommand(intent: Intent?) {
        intent?.getStringExtra("prefill_command")?.let { prefill ->
            commandInput.setText(prefill)
            commandInput.setSelection(prefill.length)
        }
    }

    private fun renderTrace(trace: ExecutionTrace): String {
        return buildString {
            appendLine("Goal: ${trace.goal.text}")
            appendLine("Category: ${trace.goal.category}")
            appendLine("Strategy: ${trace.strategy}")
            appendLine()
            trace.entries.forEach { entry ->
                appendLine("${statusPrefix(entry)} [${entry.status}] ${entry.description}")
                appendLine("   Executor: ${entry.executorName}")
                appendLine("   Detail: ${entry.detail}")
                appendLine()
            }
            appendLine(trace.finalMessage)
            if (trace.awaitingConfirmation) {
                appendLine("[pause] Confirmation required before continuing.")
            }
        }
    }

    private fun statusPrefix(entry: TraceEntry): String {
        return when (entry.status) {
            StepStatus.SUCCESS -> "[ok]"
            StepStatus.PENDING_CONFIRMATION -> "[wait]"
            StepStatus.BLOCKED -> "[blocked]"
            StepStatus.SKIPPED -> "[skip]"
        }
    }
}
