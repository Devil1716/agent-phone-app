package com.gemma.agentphone.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiSettingsStoreTest {
    @Test
    fun savesAndLoadsSettingsThroughKeyValueStore() {
        val keyValueStore = InMemoryKeyValueStore()
        val store = AiSettingsStore(keyValueStore)
        val updated = AiSettings.defaultGemma().copy(
            activeProvider = "qwen-local",
            customPrompt = "Prefer WhatsApp for messages",
            relayEndpoint = "http://10.0.2.2:8080",
            modelDownloadUrl = "https://example.com/model.task",
            huggingFaceToken = "hf_test_token"
        )

        store.save(updated)
        val restored = store.load()

        assertThat(restored.activeProvider).isEqualTo("qwen-local")
        assertThat(restored.customPrompt).isEqualTo("Prefer WhatsApp for messages")
        assertThat(restored.relayEndpoint).isEqualTo("http://10.0.2.2:8080")
        assertThat(restored.modelDownloadUrl).isEqualTo("https://example.com/model.task")
        assertThat(restored.huggingFaceToken).isEqualTo("hf_test_token")
    }
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, defaultValue: String): String {
        return values[key] as? String ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
}
