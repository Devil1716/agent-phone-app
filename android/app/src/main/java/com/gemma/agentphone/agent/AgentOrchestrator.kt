package com.gemma.agentphone.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.gemma.agentphone.MainActivity
import com.gemma.agentphone.R
import com.gemma.agentphone.diagnostics.AppLogger
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.GemmaModelManager
import com.gemma.agentphone.model.GoalCategory
import kotlinx.coroutines.flow.StateFlow

class AgentOrchestrator(
    private val context: Context,
    private val settingsRepository: AiSettingsRepository = AiSettingsRepository(context),
    private val modelManager: GemmaModelManager = GemmaModelManager(context)
) : AgentRuntime {
    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    private val goalInterpreter = RuleBasedGoalInterpreter()
    private val appLaunchExecutor by lazy { AppLaunchExecutor(AndroidInstalledAppResolver(context)) }
    private val browserExecutor = BrowserExecutor()
    private val intentExecutor = IntentExecutor()

    override val status: StateFlow<AgentStatus> = AgentSessionStore.status
    override val logs: StateFlow<List<AgentLogEntry>> = AgentSessionStore.logs
    override val downloadState = modelManager.downloadState

    override fun isModelReady(): Boolean = modelManager.isModelReady()

    override suspend fun execute(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) {
            return
        }

        AgentSessionStore.begin(trimmed)
        AppLogger.i(context, TAG, "Executing command: $trimmed")

        try {
            if (tryExecuteFastPath(trimmed)) {
                AppLogger.i(context, TAG, "Command completed using deterministic fast path.")
                return
            }

            val settings = settingsRepository.load()
            if (!modelManager.isModelReady()) {
                AgentSessionStore.updateStatus(
                    AgentStatus.Downloading(context.getString(R.string.agent_status_downloading))
                )
                modelManager.ensureModelReady(
                    downloadUrl = settings.modelDownloadUrl,
                    huggingFaceToken = settings.huggingFaceToken
                )
            }

            if (!modelManager.isModelReady()) {
                val message = context.getString(R.string.model_not_ready)
                appendLog("Model", message, success = false)
                AgentSessionStore.updateStatus(AgentStatus.Failed(message))
                return
            }

            ContextCompat.startForegroundService(
                context,
                AgentAutomationService.buildStartIntent(context, trimmed)
            )
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Execution failed for command: $trimmed", throwable)
            val message = throwable.localizedMessage ?: "Error running the agent."
            appendLog("Error", message, success = false)
            AgentSessionStore.updateStatus(AgentStatus.Failed(message))
        }
    }

    override suspend fun startModelDownload() {
        try {
            val settings = settingsRepository.load()
            AppLogger.i(context, TAG, "User started Gemma model download from the main screen.")
            AgentSessionStore.updateStatus(
                AgentStatus.Downloading(context.getString(R.string.agent_status_downloading))
            )
            modelManager.startOrResumeDownload(settings.modelDownloadUrl, settings.huggingFaceToken)
            AgentSessionStore.updateStatus(AgentStatus.Idle)
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Gemma model download failed from the main screen.", throwable)
            val message = throwable.localizedMessage ?: "Unable to download the Gemma model."
            appendLog("Model", message, success = false)
            AgentSessionStore.updateStatus(AgentStatus.Failed(message))
        }
    }

    override suspend fun importModel(uri: Uri) {
        try {
            AppLogger.i(context, TAG, "User imported a Gemma model from the main screen.")
            modelManager.importModel(uri)
            AgentSessionStore.updateStatus(AgentStatus.Idle)
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Gemma model import failed from the main screen.", throwable)
            val message = throwable.localizedMessage ?: "Unable to import the Gemma model."
            appendLog("Model", message, success = false)
            AgentSessionStore.updateStatus(AgentStatus.Failed(message))
        }
    }

    override fun cancelExecution() {
        runCatching {
            context.startService(AgentAutomationService.buildStopIntent(context))
        }
        AgentSessionStore.updateStatus(AgentStatus.Stopped("Execution stopped."))
    }

    private fun appendLog(
        title: String,
        detail: String,
        success: Boolean?,
        step: AgentStep? = null
    ) {
        AgentSessionStore.appendLog(
            AgentLogEntry(title = title, detail = detail, success = success, step = step)
        )
    }

    private fun tryExecuteFastPath(command: String): Boolean {
        val goal = goalInterpreter.interpret(command)
        val step = when (goal.category) {
            GoalCategory.OPEN_SETTINGS -> TaskStep(
                id = "fast-settings",
                type = StepType.OPEN_SYSTEM_SETTINGS,
                description = goal.understanding ?: "Open Android settings.",
                targetApp = goal.targetApp,
                payload = goal.targetValue
            )

            GoalCategory.WEB_SEARCH -> TaskStep(
                id = "fast-web-search",
                type = StepType.OPEN_BROWSER_SEARCH,
                description = goal.understanding ?: "Open a browser search.",
                targetApp = goal.targetApp,
                payload = goal.targetValue
            )

            GoalCategory.OPEN_MAPS -> TaskStep(
                id = "fast-maps",
                type = StepType.OPEN_MAPS,
                description = goal.understanding ?: "Open navigation.",
                targetApp = goal.targetApp,
                payload = goal.targetValue
            )

            GoalCategory.PLAY_MEDIA -> TaskStep(
                id = "fast-media-search",
                type = StepType.OPEN_MEDIA_SEARCH,
                description = goal.understanding ?: "Search for media.",
                targetApp = goal.targetApp,
                payload = goal.targetValue
            )

            GoalCategory.GENERAL_APP_CONTROL -> null
            else -> null
        } ?: return false

        val result = when (step.type) {
            StepType.OPEN_APP -> appLaunchExecutor.execute(step, fastPathObservation())
            StepType.OPEN_SYSTEM_SETTINGS,
            StepType.OPEN_MAPS -> intentExecutor.execute(step, fastPathObservation())

            StepType.OPEN_BROWSER_SEARCH,
            StepType.OPEN_MEDIA_SEARCH -> browserExecutor.execute(step, fastPathObservation())

            else -> return false
        }

        appendLog(
            title = "Fast path",
            detail = result.message,
            success = result.status == StepStatus.SUCCESS,
            step = step.toAgentStep()
        )

        val externalAction = result.externalAction
        return if (externalAction != null) {
            AgentSessionStore.updateStatus(AgentStatus.Executing(step.toAgentStep(), 1, 1))
            launchExternalAction(externalAction.spec)
            AgentSessionStore.updateStatus(AgentStatus.Completed(result.message))
            true
        } else {
            AgentSessionStore.updateStatus(AgentStatus.Failed(result.message))
            true
        }
    }

    private fun fastPathObservation(): ScreenObservation {
        return ScreenObservation(
            foregroundApp = context.packageName,
            visibleText = emptyList(),
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun TaskStep.toAgentStep(): AgentStep {
        return AgentStep(
            action = type.name,
            target = targetApp.orEmpty(),
            value = payload.orEmpty(),
            reason = description
        )
    }

    private fun launchExternalAction(spec: IntentSpec) {
        if (context is Activity) {
            MainActivity.externalActionLauncher.launch(context, spec)
            return
        }

        val intent = Intent(spec.action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            spec.data?.let { data = Uri.parse(it) }
            spec.packageName?.let(::setPackage)
        }
        context.startActivity(intent)
    }
}
