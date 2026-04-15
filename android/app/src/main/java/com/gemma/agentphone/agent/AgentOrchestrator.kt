package com.gemma.agentphone.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gemma.agentphone.R
import com.gemma.agentphone.accessibility.ScreenStateReader
import com.gemma.agentphone.actions.ActionDispatcher
import com.gemma.agentphone.MainActivity
import com.gemma.agentphone.diagnostics.AppLogger
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.GemmaInferenceEngine
import com.gemma.agentphone.model.GemmaModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

class AgentOrchestrator(
    private val context: Context,
    private val settingsRepository: AiSettingsRepository = AiSettingsRepository(context),
    private val modelManager: GemmaModelManager = GemmaModelManager(context),
    private val engineFactory: (Context, GemmaModelManager) -> TextGenerationEngine = { appContext, manager ->
        GemmaInferenceEngine(appContext, manager)
    }
) : AgentRuntime {
    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    private val _logs = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    private val stopRequested = AtomicBoolean(false)
    private val goalInterpreter = RuleBasedGoalInterpreter()
    private val appLaunchExecutor by lazy { AppLaunchExecutor(AndroidInstalledAppResolver(context)) }
    private val browserExecutor = BrowserExecutor()
    private val intentExecutor = IntentExecutor()

    override val status: StateFlow<AgentStatus> = _status.asStateFlow()
    override val logs: StateFlow<List<AgentLogEntry>> = _logs.asStateFlow()
    override val downloadState = modelManager.downloadState

    override fun isModelReady(): Boolean = modelManager.isModelReady()

    override suspend fun execute(command: String) {
        stopRequested.set(false)
        _logs.value = emptyList()
        AppLogger.i(context, TAG, "Executing command: $command")

        try {
            if (tryExecuteFastPath(command)) {
                AppLogger.i(context, TAG, "Command completed using deterministic fast path.")
                return
            }

            val settings = settingsRepository.load()
            if (!modelManager.isModelReady()) {
                _status.value = AgentStatus.Downloading(context.getString(R.string.agent_status_downloading))
                modelManager.ensureModelReady(
                    downloadUrl = settings.modelDownloadUrl,
                    huggingFaceToken = settings.huggingFaceToken
                )
            }

            if (!modelManager.isModelReady()) {
                _status.value = AgentStatus.Failed(context.getString(R.string.model_not_ready))
                appendLog("Model", context.getString(R.string.model_not_ready), success = false)
                return
            }

            val engine = engineFactory(context, modelManager)
            val planner = PlannerAgent(engine)
            val verifier = VerifierAgent(engine)
            val executor = ExecutorAgent(
                plannerAgent = planner,
                verifierAgent = verifier,
                actionDispatcher = ActionDispatcher(context),
                screenStateReader = ScreenStateReader()
            )

            _status.value = AgentStatus.Planning(command)
            appendLog("Plan", "Gemma 4 is planning the request.", success = null)
            val plan = planner.plan(command)
            plan.forEachIndexed { index, step ->
                appendLog("Step ${index + 1}", step.summary(), success = null, step = step)
            }

            val result = executor.execute(
                command = command,
                initialPlan = plan,
                emitStatus = { status -> _status.value = status },
                emitLog = { entry -> _logs.update { current -> current + entry } },
                shouldStop = { stopRequested.get() }
            )

            _status.value = if (result.success) {
                AgentStatus.Completed(result.summary)
            } else if (stopRequested.get()) {
                AgentStatus.Stopped(result.summary)
            } else {
                AgentStatus.Failed(result.summary)
            }
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Execution failed for command: $command", throwable)
            val message = throwable.localizedMessage ?: "Error running the agent."
            appendLog("Error", message, success = false)
            _status.value = AgentStatus.Failed(message)
        }
    }

    override suspend fun startModelDownload() {
        try {
            val settings = settingsRepository.load()
            AppLogger.i(context, TAG, "User started Gemma 4 download from the main screen.")
            _status.value = AgentStatus.Downloading(context.getString(R.string.agent_status_downloading))
            modelManager.startOrResumeDownload(settings.modelDownloadUrl, settings.huggingFaceToken)
            _status.value = AgentStatus.Idle
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Gemma 4 download failed from the main screen.", throwable)
            val message = throwable.localizedMessage ?: "Unable to download Gemma 4."
            appendLog("Model", message, success = false)
            _status.value = AgentStatus.Failed(message)
        }
    }

    override suspend fun importModel(uri: Uri) {
        try {
            AppLogger.i(context, TAG, "User imported a Gemma 4 model from the main screen.")
            modelManager.importModel(uri)
            _status.value = AgentStatus.Idle
        } catch (throwable: Throwable) {
            AppLogger.e(context, TAG, "Gemma 4 import failed from the main screen.", throwable)
            val message = throwable.localizedMessage ?: "Unable to import the Gemma 4 model."
            appendLog("Model", message, success = false)
            _status.value = AgentStatus.Failed(message)
        }
    }

    override fun cancelExecution() {
        stopRequested.set(true)
        _status.value = AgentStatus.Stopped("Execution stopped.")
    }

    private fun appendLog(
        title: String,
        detail: String,
        success: Boolean?,
        step: AgentStep? = null
    ) {
        _logs.update { current ->
            current + AgentLogEntry(title = title, detail = detail, success = success, step = step)
        }
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

            GoalCategory.GENERAL_APP_CONTROL -> {
                if (goal.shouldOpenAppFirst && !goal.targetApp.isNullOrBlank()) {
                    TaskStep(
                        id = "fast-app-launch",
                        type = StepType.OPEN_APP,
                        description = goal.understanding ?: "Open ${goal.targetApp}.",
                        targetApp = goal.targetApp,
                        payload = goal.targetValue
                    )
                } else {
                    null
                }
            }

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
            _status.value = AgentStatus.Executing(step.toAgentStep(), 1, 1)
            launchExternalAction(externalAction.spec)
            _status.value = AgentStatus.Completed(result.message)
            true
        } else {
            _status.value = AgentStatus.Failed(result.message)
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
