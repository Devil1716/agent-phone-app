package com.gemma.agentphone.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gemma.agentphone.R
import com.gemma.agentphone.agent.AgentLogEntry

class AgentLogAdapter : RecyclerView.Adapter<AgentLogAdapter.AgentLogViewHolder>() {
    private val entries = mutableListOf<AgentLogEntry>()

    fun submit(entries: List<AgentLogEntry>) {
        this.entries.clear()
        this.entries.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cot_step, parent, false)
        return AgentLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: AgentLogViewHolder, position: Int) {
        holder.bind(entries[position], position + 1)
    }

    override fun getItemCount(): Int = entries.size

    class AgentLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stepNumber: TextView = view.findViewById(R.id.cotStepNumber)
        private val executorName: TextView = view.findViewById(R.id.cotExecutorName)
        private val description: TextView = view.findViewById(R.id.cotDescription)
        private val thought: TextView = view.findViewById(R.id.cotThought)
        private val detail: TextView = view.findViewById(R.id.cotDetail)
        private val statusBadge: TextView = view.findViewById(R.id.cotStatusBadge)

        fun bind(entry: AgentLogEntry, number: Int) {
            stepNumber.text = number.toString()
            executorName.text = entry.title
            description.text = entry.step?.summary() ?: entry.detail
            thought.visibility = View.GONE
            detail.text = entry.detail
            statusBadge.text = when (entry.success) {
                true -> "OK"
                false -> "FAIL"
                null -> "INFO"
            }
            val color = when (entry.success) {
                true -> R.color.traceSuccess
                false -> R.color.traceBlocked
                null -> R.color.tracePending
            }
            statusBadge.setTextColor(itemView.context.getColor(color))
        }
    }
}
