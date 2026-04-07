package com.gemma.agentphone

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ConfirmationDialogFragment : DialogFragment() {

    var onConfirm: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val stepDescription = arguments?.getString(ARG_STEP_DESCRIPTION) ?: "Perform this action?"
        val stepReason = arguments?.getString(ARG_STEP_REASON) ?: ""

        val message = buildString {
            appendLine(stepDescription)
            if (stepReason.isNotBlank()) {
                appendLine()
                appendLine("Reason: $stepReason")
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirmation_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirmation_proceed) { _, _ ->
                onConfirm?.invoke()
            }
            .setNegativeButton(R.string.confirmation_cancel) { _, _ ->
                onCancel?.invoke()
            }
            .setCancelable(false)
            .create()
    }

    companion object {
        const val TAG = "ConfirmationDialog"
        private const val ARG_STEP_DESCRIPTION = "step_description"
        private const val ARG_STEP_REASON = "step_reason"

        fun newInstance(stepDescription: String, stepReason: String): ConfirmationDialogFragment {
            return ConfirmationDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STEP_DESCRIPTION, stepDescription)
                    putString(ARG_STEP_REASON, stepReason)
                }
            }
        }
    }
}
