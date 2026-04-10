package com.gemma.agentphone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gemma.agentphone.model.AiSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsRepositoryInstrumentedTest {
    @Test
    fun persistsRelayEndpoint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AiSettingsRepository(context)
        val updated = repository.load().copy(relayEndpoint = "http://10.0.2.2:8080")

        repository.save(updated)

        assertEquals("http://10.0.2.2:8080", repository.load().relayEndpoint)
    }
}
