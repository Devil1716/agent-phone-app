package com.gemma.agentphone.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiProviderRegistryTest {
    private val registry = AiProviderRegistry()

    @Test
    fun listsOpenSourceAndRelayProviders() {
        val providerIds = registry.listProviders().map { it.id }

        assertThat(providerIds).containsAtLeast("gemma-local", "qwen-local", "phi-local", "llama-local", "relay-gemma")
    }
}
