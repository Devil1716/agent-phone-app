package com.gemma.agentphone.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

data class ResolvedAppLaunch(
    val packageName: String,
    val label: String
)

fun interface InstalledAppResolver {
    fun resolve(query: String): ResolvedAppLaunch?
}

class AndroidInstalledAppResolver(context: Context) : InstalledAppResolver {
    private val packageManager = context.packageManager
    private val launchableApps: List<ResolvedAppLaunch> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull(::toResolvedApp)
            .distinctBy { it.packageName }
    }

    override fun resolve(query: String): ResolvedAppLaunch? {
        val normalizedQuery = query.normalizeForMatch()
        if (normalizedQuery.isBlank()) {
            return null
        }

        return launchableApps.firstOrNull { it.label.normalizeForMatch() == normalizedQuery }
            ?: launchableApps.firstOrNull { it.packageName.substringAfterLast('.').normalizeForMatch() == normalizedQuery }
            ?: launchableApps.firstOrNull { it.label.normalizeForMatch().startsWith(normalizedQuery) }
            ?: launchableApps.firstOrNull { it.label.normalizeForMatch().contains(normalizedQuery) }
    }

    private fun toResolvedApp(info: ResolveInfo): ResolvedAppLaunch? {
        val packageName = info.activityInfo?.packageName ?: return null
        val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
        if (label.isBlank()) {
            return null
        }
        return ResolvedAppLaunch(packageName = packageName, label = label)
    }

    private fun String.normalizeForMatch(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

class AppLaunchExecutor(
    private val appResolver: InstalledAppResolver
) : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type == StepType.OPEN_APP
    }

    override fun execute(step: TaskStep, observation: ScreenObservation): StepResult {
        val query = step.targetApp ?: step.payload ?: return StepResult(
            stepId = step.id,
            status = StepStatus.SKIPPED,
            message = "No app name was provided for launch.",
            executorName = "AppLaunchExecutor"
        )

        val resolved = appResolver.resolve(query)
            ?: return StepResult(
                stepId = step.id,
                status = StepStatus.SKIPPED,
                message = "Could not find an installed app matching \"$query\".",
                executorName = "AppLaunchExecutor"
            )

        return StepResult(
            stepId = step.id,
            status = StepStatus.SUCCESS,
            message = "Prepared launch for ${resolved.label}.",
            executorName = "AppLaunchExecutor",
            externalAction = ExternalActionRequest(
                IntentSpec(
                    action = Intent.ACTION_MAIN,
                    packageName = resolved.packageName
                )
            )
        )
    }
}
