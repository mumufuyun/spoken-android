/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    override suspend fun complete(systemPrompt: String, userText: String): String =
        withContext(Dispatchers.IO) {
            val url = config.baseUrl.trimEnd('/') + "/chat/completions"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(
                    buildRequestBody(config.model, systemPrompt, userText)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("AI HTTP ${response.code}")
                parseContent(response.body?.string().orEmpty())
                    ?: throw IOException("AI response carries no content")
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
