package com.gemma.agentphone

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.AiSettingsRepository

class SettingsActivity : AppCompatActivity() {
    private val providerRegistry = AiProviderRegistry()
    private lateinit var providerSpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var fallbackProviderSpinner: Spinner
    private lateinit var fallbackModelSpinner: Spinner
    private lateinit var autonomySpinner: Spinner
    private lateinit var cloudFallbackCheckbox: CheckBox
    private lateinit var relayEndpointInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        providerSpinner = findViewById(R.id.providerSpinner)
        modelSpinner = findViewById(R.id.modelSpinner)
        fallbackProviderSpinner = findViewById(R.id.fallbackProviderSpinner)
        fallbackModelSpinner = findViewById(R.id.fallbackModelSpinner)
        autonomySpinner = findViewById(R.id.autonomySpinner)
        cloudFallbackCheckbox = findViewById(R.id.cloudFallbackCheckbox)
        relayEndpointInput = findViewById(R.id.relayEndpointInput)

        val settingsRepository = AiSettingsRepository(this)
        val settings = settingsRepository.load()
        val providers = providerRegistry.listProviders()
        val providerIds = providers.map { it.id }
        val autonomyModes = listOf("assist", "confirmed-action", "autonomous")

        providerSpinner.adapter = createAdapter(providerIds)
        fallbackProviderSpinner.adapter = createAdapter(providerIds)
        autonomySpinner.adapter = createAdapter(autonomyModes)

        providerSpinner.setSelection(providerIds.indexOf(settings.activeProvider).coerceAtLeast(0))
        fallbackProviderSpinner.setSelection(providerIds.indexOf(settings.fallbackProvider).coerceAtLeast(0))
        autonomySpinner.setSelection(autonomyModes.indexOf(settings.autonomyMode).coerceAtLeast(0))
        cloudFallbackCheckbox.isChecked = settings.allowCloudFallback
        relayEndpointInput.setText(settings.relayEndpoint)

        updateModelSpinner(modelSpinner, providers, settings.activeProvider, settings.activeModel)
        updateModelSpinner(fallbackModelSpinner, providers, settings.fallbackProvider, settings.fallbackModel)

        providerSpinner.setOnItemSelectedListener(SimpleItemSelectedListener {
            updateModelSpinner(modelSpinner, providers, providerSpinner.selectedItem.toString(), null)
        })

        fallbackProviderSpinner.setOnItemSelectedListener(SimpleItemSelectedListener {
            updateModelSpinner(fallbackModelSpinner, providers, fallbackProviderSpinner.selectedItem.toString(), null)
        })

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            val nextSettings = AiSettings(
                activeProvider = providerSpinner.selectedItem.toString(),
                activeModel = modelSpinner.selectedItem.toString(),
                fallbackProvider = fallbackProviderSpinner.selectedItem.toString(),
                fallbackModel = fallbackModelSpinner.selectedItem.toString(),
                autonomyMode = autonomySpinner.selectedItem.toString(),
                allowCloudFallback = cloudFallbackCheckbox.isChecked,
                relayEndpoint = relayEndpointInput.text.toString().trim()
            )

            settingsRepository.save(nextSettings)
            finish()
        }
    }

    private fun createAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
    }

    private fun updateModelSpinner(
        spinner: Spinner,
        providers: List<AiProviderDescriptor>,
        providerId: String,
        selectedModel: String?
    ) {
        val provider = providers.firstOrNull { it.id == providerId } ?: providers.first()
        spinner.adapter = createAdapter(provider.models)
        val index = provider.models.indexOf(selectedModel).takeIf { it >= 0 } ?: 0
        spinner.setSelection(index)
    }
}
