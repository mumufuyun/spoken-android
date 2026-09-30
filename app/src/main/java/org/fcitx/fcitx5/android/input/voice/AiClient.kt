/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSec: Int
)

/** Abstraction seam so [AiProcessor] can be tested with a fake client. */
interface AiCompletionClient {
    suspend fun complete(systemPrompt: String, userText: String): String
}

@Serializable
private data class ChatMessage(val role: String = "", val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double
)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val message: ChatMessage? = null)
}

/** Non-streaming OpenAI-compatible /chat/completions client. */
class AiClient(
    private val config: AiConfig,
    private val client: OkHttpClient = defaultClient(config.timeoutSec)
) : AiCompletionClient {

    override suspend fun complete(systemPrompt: String, userText: String): String {
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(
                buildRequestBody(config.model, systemPrompt, userText)
                    .toRequestBody("application/json".toMediaType())
            )
            .build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        when {
                            !it.isSuccessful ->
                                continuation.resumeWithException(IOException("AI HTTP ${it.code}"))
                            else -> {
                                val content = parseContent(it.body?.string().orEmpty())
                                if (content != null) continuation.resume(content)
                                else continuation.resumeWithException(
                                    IOException("AI response carries no content")
                                )
                            }
                        }
                    }
                }
            })
        }
    }

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun buildRequestBody(model: String, systemPrompt: String, userText: String): String {
            val messages = buildList {
                if (systemPrompt.isNotBlank()) add(ChatMessage("system", systemPrompt))
                add(ChatMessage("user", userText))
            }
            return json.encodeToString(ChatRequest(model, messages, temperature = 0.3))
        }

        fun parseContent(raw: String): String? = runCatching {
            json.decodeFromString<ChatResponse>(raw).choices.firstOrNull()?.message?.content
        }.getOrNull()

        private fun defaultClient(timeoutSec: Int): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
    }
}
