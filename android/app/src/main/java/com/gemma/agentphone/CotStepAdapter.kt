package com.gemma.agentphone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gemma.agentphone.agent.StepStatus
import com.gemma.agentphone.agent.TraceEntry

/**
 * RecyclerView adapter that powers the Chain of Thought (CoT) mini-window.
 * Each item represents one step in the agent's execution trace,
 * rendered in real time as the agent "thinks" and "acts".
 */
class CotStepAdapter : RecyclerView.Adapter<CotStepAdapter.StepViewHolder>() {

    private val steps = mutableListOf<TraceEntry>()

    /** Append a new step and animate its insertion. */
    fun addStep(entry: TraceEntry) {
        steps.add(entry)
        notifyItemInserted(steps.size - 1)
    }

    /** Replace all steps at once (e.g. when showing a completed trace). */
    fun submitAll(entries: List<TraceEntry>) {
        steps.clear()
        steps.addAll(entries)
        notifyDataSetChanged()
    }

    /** Clear all steps for a new execution. */
    fun clear() {
        val count = steps.size
        steps.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun stepCount(): Int = steps.size

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cot_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position], position + 1)
    }

    class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stepNumber: TextView = view.findViewById(R.id.cotStepNumber)
        private val executorName: TextView = view.findViewById(R.id.cotExecutorName)
        private val description: TextView = view.findViewById(R.id.cotDescription)
        private val thought: TextView = view.findViewById(R.id.cotThought)
        private val detail: TextView = view.findViewById(R.id.cotDetail)
        private val statusBadge: TextView = view.findViewById(R.id.cotStatusBadge)

        fun bind(entry: TraceEntry, number: Int) {
            stepNumber.text = number.toString()
            executorName.text = entry.executorName
            description.text = entry.description

            if (!entry.thought.isNullOrBlank()) {
                thought.text = entry.thought
                thought.visibility = View.VISIBLE
            } else {
                thought.visibility = View.GONE
            }

            detail.text = entry.detail

            // Status badge styling
            when (entry.status) {
                StepStatus.SUCCESS -> {
                    statusBadge.text = "✓ OK"
                    statusBadge.setTextColor(itemView.context.getColor(R.color.traceSuccess))
                }
                StepStatus.PENDING_CONFIRMATION -> {
                    statusBadge.text = "⏳ WAIT"
                    statusBadge.setTextColor(itemView.context.getColor(R.color.tracePending))
                }
                StepStatus.BLOCKED -> {
                    statusBadge.text = "✗ BLOCKED"
                    statusBadge.setTextColor(itemView.context.getColor(R.color.traceBlocked))
                }
                StepStatus.SKIPPED -> {
                    statusBadge.text = "— SKIP"
                    statusBadge.setTextColor(itemView.context.getColor(R.color.traceSkipped))
                }
            }
        }
    }
}
