package com.gemma.agentphone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.gemma.agentphone.agent.IntentSpec

fun interface ExternalActionLauncher {
    fun launch(activity: Activity, spec: IntentSpec)
}

object DefaultExternalActionLauncher : ExternalActionLauncher {
    override fun launch(activity: Activity, spec: IntentSpec) {
        if (spec.packageName != null && spec.action == Intent.ACTION_MAIN && spec.data == null) {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(spec.packageName)
            if (launchIntent != null) {
                activity.startActivity(launchIntent)
                return
            }
        }

        val intent = Intent(spec.action).apply {
            spec.data?.let { data = Uri.parse(it) }
            spec.packageName?.let(::setPackage)
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val target = spec.packageName ?: spec.data ?: spec.action
            Toast.makeText(activity, "Unable to open $target on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}
