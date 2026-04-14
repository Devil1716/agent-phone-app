package com.gemma.agentphone

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gemma.agentphone.model.ExecutionHistoryEntry
import com.gemma.agentphone.model.ExecutionHistoryRepository
import com.gemma.agentphone.model.SharedPreferencesKeyValueStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var repository: ExecutionHistoryRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyContainer: View
    private val adapter = HistoryAdapter { entry ->
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("prefill_command", entry.commandText)
        }
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        repository = ExecutionHistoryRepository(
            SharedPreferencesKeyValueStore(
                getSharedPreferences("execution_history", MODE_PRIVATE)
            )
        )

        recyclerView = findViewById(R.id.historyRecycler)
        emptyContainer = findViewById(R.id.emptyHistoryContainer)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.clearHistoryButton).setOnClickListener {
            repository.clear()
            refreshList()
        }

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        refreshList()
    }

    private fun refreshList() {
        val entries = repository.loadAll()
        adapter.submitList(entries)
        emptyContainer.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
    }
}

private class HistoryAdapter(
    private val onItemClick: (ExecutionHistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryViewHolder>() {

    private var items: List<ExecutionHistoryEntry> = emptyList()

    fun submitList(newItems: List<ExecutionHistoryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }
}

private class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val commandText: TextView = view.findViewById(R.id.historyCommand)
    private val strategyText: TextView = view.findViewById(R.id.historyStrategy)
    private val categoryText: TextView = view.findViewById(R.id.historyCategory)
    private val resultText: TextView = view.findViewById(R.id.historyResult)
    private val timestampText: TextView = view.findViewById(R.id.historyTimestamp)

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    fun bind(entry: ExecutionHistoryEntry, onClick: (ExecutionHistoryEntry) -> Unit) {
        commandText.text = entry.commandText
        strategyText.text = entry.strategy
        categoryText.text = entry.category
        resultText.text = entry.resultSummary
        timestampText.text = dateFormat.format(Date(entry.timestampMs))
        itemView.setOnClickListener { onClick(entry) }
    }
}
