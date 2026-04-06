package com.gemma.agentphone

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.gemma.agentphone.agent.IntentSpec

fun interface ExternalActionLauncher {
    fun launch(activity: Activity, spec: IntentSpec)
}

object DefaultExternalActionLauncher : ExternalActionLauncher {
    override fun launch(activity: Activity, spec: IntentSpec) {
        val intent = Intent(spec.action).apply {
            spec.data?.let { data = Uri.parse(it) }
            spec.packageName?.let(::setPackage)
        }
        activity.startActivity(intent)
    }
}
