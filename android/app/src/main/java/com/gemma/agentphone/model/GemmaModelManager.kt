package com.gemma.agentphone.model

import android.content.Context
import android.net.Uri
import com.gemma.agentphone.BuildConfig
import com.gemma.agentphone.diagnostics.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.math.max

data class ModelDownloadState(
    val isReady: Boolean = false,
    val isDownloading: Boolean = false,
    val isVerifying: Boolean = false,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = ""
)

class GemmaModelManager(
    private val context: Context,
    private val modelFileName: String = BuildConfig.GEMMA4_MODEL_FILENAME,
    private val expectedSha256: String = BuildConfig.GEMMA4_MODEL_SHA256,
    private val maxDownloadRetries: Int = MAX_DOWNLOAD_RETRIES,
    private val retryBackoffMs: Long = RETRY_BACKOFF_MS,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GemmaModelManager"
        private const val MODELS_DIR = "models"
        private const val PART_SUFFIX = ".part"
        private const val VERIFIED_SUFFIX = ".verified"
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 1_500L
    }

    private val downloadMutex = Mutex()
    private val modelsDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
    private val finalFile = File(modelsDir, modelFileName)
    private val partialFile = File(modelsDir, "$modelFileName$PART_SUFFIX")
    private val verificationFile = File(modelsDir, "$modelFileName$VERIFIED_SUFFIX")
    private val _downloadState = MutableStateFlow(initialState())

    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    fun isModelReady(): Boolean = hasVerifiedModel()

    fun getModelPath(): String = finalFile.absolutePath

    suspend fun ensureModelReady(
        downloadUrl: String = BuildConfig.GEMMA4_MODEL_URL,
        huggingFaceToken: String = ""
    ) {
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                if (hasVerifiedModel()) {
                    _downloadState.value = readyState("Gemma 4 model is ready.")
                    return@withContext
                }

                if (finalFile.exists()) {
                    AppLogger.i(context, TAG, "Found Gemma 4 file without verification marker. Verifying now.")
                    _downloadState.value = ModelDownloadState(
                        isVerifying = true,
                        downloadedBytes = finalFile.length(),
                        totalBytes = finalFile.length(),
                        progressPercent = 100,
                        message = "Verifying Gemma 4"
                    )
                    if (verifyAndMark(finalFile, expectedChecksumFor(downloadUrl))) {
                        _downloadState.value = readyState("Gemma 4 model is ready.")
                        return@withContext
                    }
                }

                downloadWithRetries(downloadUrl, huggingFaceToken)
            }
        }
    }

    suspend fun startOrResumeDownload(
        downloadUrl: String = BuildConfig.GEMMA4_MODEL_URL,
        huggingFaceToken: String = ""
    ) {
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                if (hasVerifiedModel()) {
                    _downloadState.value = readyState("Gemma 4 model is already ready.")
                    return@withContext
                }
                downloadWithRetries(downloadUrl, huggingFaceToken)
            }
        }
    }

    suspend fun importModel(uri: Uri) {
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                AppLogger.i(context, TAG, "Importing Gemma 4 model from user-selected file.")
                clearVerificationMarker()
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected model file could not be opened." }
                    FileOutputStream(finalFile, false).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                verifyOrThrow(finalFile, expectedChecksum = null)
                _downloadState.value = readyState("Gemma 4 model imported successfully.")
            }
        }
    }

    suspend fun invalidateModel(reason: String) {
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                AppLogger.w(context, TAG, "Invalidating saved Gemma model. Reason: $reason")
                clearVerificationMarker()
                partialFile.delete()
                finalFile.delete()
                _downloadState.value = ModelDownloadState(
                    isReady = false,
                    isDownloading = false,
                    isVerifying = false,
                    message = reason
                )
            }
        }
    }

    private suspend fun downloadWithRetries(downloadUrl: String, huggingFaceToken: String) {
        require(downloadUrl.isNotBlank()) { "A Gemma 4 download URL is required." }

        var attempt = 0
        var lastFailure: Throwable? = null
        while (attempt < maxDownloadRetries) {
            attempt += 1
            try {
                AppLogger.i(context, TAG, "Starting Gemma 4 download attempt $attempt.")
                downloadInternal(downloadUrl, huggingFaceToken)
                AppLogger.i(context, TAG, "Gemma 4 model download completed successfully.")
                return
            } catch (throwable: Throwable) {
                lastFailure = throwable
                AppLogger.w(
                    context,
                    TAG,
                    "Gemma 4 download attempt $attempt failed. Partial bytes=${partialFile.length()}",
                    throwable
                )
                val canRetry = attempt < maxDownloadRetries && !isStorageFull(throwable)
                _downloadState.value = failureState(
                    message = buildFailureMessage(throwable, canRetry),
                    downloadedBytes = partialFile.length()
                )
                if (!canRetry) {
                    break
                }
                delay(retryBackoffMs * attempt)
            }
        }

        throw IllegalStateException(
            lastFailure?.localizedMessage ?: "Gemma 4 download failed after retries.",
            lastFailure
        )
    }

    private fun downloadInternal(downloadUrl: String, huggingFaceToken: String) {
        var existingBytes = partialFile.takeIf(File::exists)?.length() ?: 0L
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("Accept", "*/*")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        if (huggingFaceToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${huggingFaceToken.trim()}")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                if (partialFile.exists() && partialFile.length() > 0L) {
                    AppLogger.i(context, TAG, "Gemma 4 source reported range complete. Promoting partial file.")
                    promotePartialToFinal()
                    return
                }
                AppLogger.w(context, TAG, "Saved partial download was rejected with HTTP 416. Restarting cleanly.")
                partialFile.delete()
                clearVerificationMarker()
                throw IOException("Saved partial download was invalid. Retrying from the start.")
            }
            if (!response.isSuccessful && response.code != 206) {
                throw IOException(formatFailureMessage(response.code))
            }

            val body = requireNotNull(response.body) { "Gemma 4 download returned an empty response body." }
            if (response.code == 200 && partialFile.exists()) {
                partialFile.delete()
                existingBytes = 0L
            }

            val totalBytes = max(
                body.contentLength() + existingBytes,
                response.header("Content-Length")?.toLongOrNull()?.plus(existingBytes) ?: 0L
            )

            FileOutputStream(partialFile, existingBytes > 0L).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = existingBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        _downloadState.value = ModelDownloadState(
                            isDownloading = true,
                            progressPercent = progress(downloadedBytes, totalBytes),
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            message = "Downloading Gemma 4"
                        )
                    }
                    output.fd.sync()
                }
            }
        }

        promotePartialToFinal()
    }

    private fun promotePartialToFinal() {
        clearVerificationMarker()
        if (finalFile.exists()) {
            finalFile.delete()
        }
        partialFile.copyTo(finalFile, overwrite = true)
        _downloadState.value = ModelDownloadState(
            isVerifying = true,
            progressPercent = 100,
            downloadedBytes = finalFile.length(),
            totalBytes = finalFile.length(),
            message = "Verifying Gemma 4"
        )
        verifyOrThrow(finalFile, expectedChecksum = expectedChecksumFor(BuildConfig.GEMMA4_MODEL_URL))
        partialFile.delete()
        _downloadState.value = readyState("Gemma 4 model ready.")
    }

    private fun verifyOrThrow(file: File, expectedChecksum: String?) {
        val validBundle = looksLikeTaskBundle(file)
        val validChecksum = verifyChecksum(file, expectedChecksum)
        if (!(validBundle && validChecksum)) {
            clearVerificationMarker()
            partialFile.delete()
            file.delete()
            throw IllegalStateException(
                if (!validBundle) {
                    "The selected model is not a valid MediaPipe Gemma task bundle."
                } else {
                    "Gemma model verification failed."
                }
            )
        }
        verificationFile.writeText(computeChecksum(file))
    }

    private fun verifyAndMark(file: File, expectedChecksum: String?): Boolean {
        val matches = verifyChecksum(file, expectedChecksum) && looksLikeTaskBundle(file)
        if (matches) {
            verificationFile.writeText(computeChecksum(file))
            return true
        }
        return false
    }

    private fun verifyChecksum(file: File, expectedChecksum: String?): Boolean {
        if (!file.exists()) {
            return false
        }
        val expected = expectedChecksum?.trim()?.lowercase().orEmpty()
        if (expected.isBlank()) {
            return true
        }
        return computeChecksum(file) == expected
    }

    private fun hasVerifiedModel(): Boolean {
        if (!finalFile.exists() || !verificationFile.exists()) {
            return false
        }
        val marker = verificationFile.readText().trim().lowercase()
        return marker.isNotBlank() &&
            marker == computeChecksum(finalFile) &&
            looksLikeTaskBundle(finalFile)
    }

    private fun expectedChecksum(): String = expectedSha256.trim().lowercase()

    private fun expectedChecksumFor(downloadUrl: String): String? {
        val normalized = downloadUrl.trim()
        val defaultUrls = setOf(
            BuildConfig.GEMMA4_MODEL_URL,
            BuildConfig.DEFAULT_MODEL_DOWNLOAD_URL
        )
        return if (normalized in defaultUrls && expectedChecksum().isNotBlank()) {
            expectedChecksum()
        } else {
            null
        }
    }

    private fun computeChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun looksLikeTaskBundle(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            return false
        }
        return runCatching {
            ZipFile(file).use { zip ->
                val entryNames = zip.entries().asSequence().map { it.name }.toList()
                entryNames.isNotEmpty() &&
                    entryNames.any { it.equals("TOKENIZER_MODEL", ignoreCase = true) } &&
                    entryNames.any { it.startsWith("TF_LITE", ignoreCase = true) }
            }
        }.getOrElse { false }
    }

    private fun clearVerificationMarker() {
        if (verificationFile.exists()) {
            verificationFile.delete()
        }
    }

    private fun progress(downloaded: Long, total: Long): Int {
        if (total <= 0L) {
            return 0
        }
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun readyState(message: String): ModelDownloadState {
        return ModelDownloadState(
            isReady = true,
            progressPercent = 100,
            downloadedBytes = finalFile.length(),
            totalBytes = finalFile.length(),
            message = message
        )
    }

    private fun failureState(message: String, downloadedBytes: Long): ModelDownloadState {
        return ModelDownloadState(
            isReady = false,
            isDownloading = false,
            isVerifying = false,
            progressPercent = progress(downloadedBytes, downloadedBytes),
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            message = message
        )
    }

    private fun initialState(): ModelDownloadState {
        return when {
            hasVerifiedModel() -> readyState("Gemma 4 model is ready.")
            partialFile.exists() -> ModelDownloadState(
                isReady = false,
                isDownloading = false,
                downloadedBytes = partialFile.length(),
                totalBytes = partialFile.length(),
                message = "Gemma 4 download can resume from the saved partial file."
            )
            else -> ModelDownloadState(message = "Gemma 4 model is not downloaded yet.")
        }
    }

    private fun buildFailureMessage(throwable: Throwable, canRetry: Boolean): String {
        if (isStorageFull(throwable)) {
            return "Device storage is full. Free some space, then retry the Gemma 4 download."
        }
        val suffix = if (canRetry) " Retrying automatically." else ""
        return (throwable.localizedMessage ?: "Gemma 4 download failed.") + suffix
    }

    private fun isStorageFull(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }
            .mapNotNull { it.localizedMessage }
            .any { it.contains("ENOSPC", ignoreCase = true) || it.contains("No space left on device", ignoreCase = true) }
    }

    private fun formatFailureMessage(reason: Int): String {
        return when (reason) {
            401, 403 -> "Gemma 4 download requires Hugging Face access. Accept the model terms, then add a token or import the file manually."
            404 -> "The configured Gemma 4 model URL no longer exists. Update the model source in settings."
            in 500..599 -> "The Gemma 4 source is temporarily unavailable. Retry the download in a moment."
            else -> "Gemma 4 download failed with HTTP $reason."
        }
    }
}
