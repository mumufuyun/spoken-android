/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

/** Non-streaming OpenAI-compatible /chat/completions client. */
class AiClient(
    private val config: AiConfig,
    private val client: OkHttpClient = defaultClient(config.timeoutSec)
) : AiCompletionClient {

    override suspend fun complete(systemPrompt: String, userText: String): String {
        val url = chatEndpoint(config.baseUrl) ?: throw IOException("Invalid AI endpoint")
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
                                val result = runCatching {
                                    parseContent(it.body?.string().orEmpty())
                                        ?: throw IOException("AI response is empty or incomplete")
                                }
                                if (continuation.isActive) result.fold(
                                    onSuccess = { content -> continuation.resume(content) },
                                    onFailure = { error -> continuation.resumeWithException(error) }
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

        fun chatEndpoint(baseUrl: String): String? {
            val url = baseUrl.trim().toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.query != null || url.fragment != null) return null
            val path = url.encodedPath.trimEnd('/')
            return url.newBuilder().encodedPath(
                if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
            ).build().toString()
        }

        fun buildRequestBody(model: String, systemPrompt: String, userText: String): String =
            buildJsonObject {
                put("model", model)
                put("stream", false)
                put("temperature", 0.3)
                put("messages", buildJsonArray {
                    if (systemPrompt.isNotBlank()) add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userText)
                    })
                })
            }.toString()

        fun parseContent(raw: String): String? = runCatching {
            val choice = json.parseToJsonElement(raw).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            // A cut-off response is not a successful edit. Older compatible servers may omit
            // finish_reason; an explicit reason other than stop must never reach the editor.
            val reason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
            if (reason != null && reason != "stop") return null
            val message = choice["message"]?.jsonObject ?: return null
            if ((message["tool_calls"] as? JsonArray)?.isNotEmpty() == true ||
                message["function_call"]?.let { it != JsonNull } == true ||
                (message["refusal"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) return null
            val content = when (val value = message["content"]) {
                is JsonPrimitive -> value.takeIf { it.isString }?.content
                is JsonArray -> buildString {
                    for (item in value) {
                        val block = item as? JsonObject ?: return null
                        when ((block["type"] as? JsonPrimitive)?.contentOrNull) {
                            "text", "output_text" -> {
                                val part = block["text"] as? JsonPrimitive ?: return null
                                if (!part.isString) return null
                                append(part.content)
                            }
                            "reasoning", "thinking", "reasoning_content" -> Unit
                            else -> return null
                        }
                    }
                }
                else -> null
            }
            content?.takeIf { it.isNotBlank() }
        }.getOrNull()

        internal fun defaultClient(timeoutSec: Int): OkHttpClient {
            val seconds = timeoutSec.coerceIn(3, 120).toLong()
            return OkHttpClient.Builder()
                .callTimeout(seconds, TimeUnit.SECONDS)
                .connectTimeout(seconds, TimeUnit.SECONDS)
                .readTimeout(seconds, TimeUnit.SECONDS)
                .build()
        }
    }
}
