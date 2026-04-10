package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ModelDownloadManagerTest {

    @Test
    fun rejectsTinyModelFile() {
        val file = File.createTempFile("gemma-model", ".task")
        file.writeBytes(ByteArray(512))

        try {
            assertThat(ModelDownloadManager.isUsableModelFile(file)).isFalse()
        } finally {
            file.delete()
        }
    }

    @Test
    fun acceptsLargeEnoughModelFile() {
        val file = File.createTempFile("gemma-model", ".task")
        file.writeBytes(ByteArray(1_048_576))

        try {
            assertThat(ModelDownloadManager.isUsableModelFile(file)).isTrue()
        } finally {
            file.delete()
        }
    }
}
