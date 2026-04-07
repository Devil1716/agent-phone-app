package com.gemma.agentphone.model

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HttpAiProvider(
    override val descriptor: AiProviderDescriptor,
    private val baseUrl: String
) : AiProvider {

    private val client = OkHttpClient()

    override fun infer(request: AiRequest): AiResponse {
        val json = JSONObject().apply {
            put("prompt", request.prompt)
            put("mode", request.mode)
            put("targetCategory", request.targetCategory.name)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/infer")
            .post(body)
            .build()

        val startTime = System.currentTimeMillis()
        val response = client.newCall(httpRequest).execute()
        val latency = System.currentTimeMillis() - startTime

        return if (response.isSuccessful) {
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            AiResponse(
                providerId = descriptor.id,
                model = descriptor.models.first(),
                summary = responseJson.optString("summary", "Response received"),
                latencyMs = latency
            )
        } else {
            AiResponse(
                providerId = descriptor.id,
                model = descriptor.models.first(),
                summary = "Error: ${response.message}",
                latencyMs = latency
            )
        }
    }
}