/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/** Runs the real asynchronous HTTP adapter against an in-process interceptor, without credentials. */
class AiClientTransportTest {
    private val config = AiConfig("https://example.invalid/v1/chat/completions", "test-key", "test-model", 3)

    @Test
    fun successfulRequestUsesCompleteEndpointAndOnlyFinalBody() = runBlocking {
        val client = AiClient.defaultClient(3).newBuilder().addInterceptor { chain ->
            assertEquals(config.baseUrl, chain.request().url.toString())
            assertEquals("Bearer test-key", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"finish_reason":"stop","message":{"content":"正文","reasoning_content":"private"}}]}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            assertEquals("正文", withTimeout(2_000) { AiClient(config, client).complete("规则", "原文", WorkScene.WORK) })
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test
    fun bodyReadFailureResumesCallerInsteadOfLeavingItWaiting() = runBlocking {
        val body = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source() = object : ForwardingSource(Buffer()) {
                override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("synthetic read failure")
            }.buffer()
        }
        val client = AiClient.defaultClient(3).newBuilder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body).build()
        }.build()
        try {
            val result = runCatching { withTimeout(2_000) { AiClient(config, client).complete("规则", "原文", WorkScene.WORK) } }
            assertTrue(result.exceptionOrNull() is IOException)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test
    fun appliesDeadlineToWholeCallIncludingConnectionAndBody() {
        val client = AiClient.defaultClient(15)
        assertEquals(15_000, client.callTimeoutMillis)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
    }

    @Test
    fun longMeetingUsesStreamingWirePolicyAndFinalBodyOnly() = runBlocking {
        val qwen = config.copy(baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", model = "qwen3.8-flash")
        val client = AiClient.defaultClient(qwen.timeoutSec).newBuilder().addInterceptor { chain ->
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val request = Json.parseToJsonElement(body).jsonObject
            assertEquals("text/event-stream", chain.request().header("Accept"))
            assertEquals("true", request["stream"].toString())
            assertEquals("true", request["enable_thinking"].toString())
            assertEquals("32768", request["max_tokens"].toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"正文\"},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n").toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        try {
            assertEquals("正文", withTimeout(2_000) {
                AiProcessor(true, AiClient(qwen, client)).process("文".repeat(800), WorkScene.MINUTES, OutputLanguage.ORIGINAL).text
            })
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test
    fun incompleteMeetingStreamFallsBackToOriginal() = runBlocking {
        val qwen = config.copy(baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", model = "qwen3.8-flash")
        val client = AiClient.defaultClient(qwen.timeoutSec).newBuilder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"片段\"}}]}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        try {
            val original = "原".repeat(800)
            val result = withTimeout(2_000) {
                AiProcessor(true, AiClient(qwen, client)).process(original, WorkScene.MINUTES, OutputLanguage.ORIGINAL)
            }
            assertEquals(original, result.text)
            assertEquals(AiFallbackReason.REQUEST_FAILED, result.fallback)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test
    fun cancellingMeetingWhileReadingBodyCancelsCallWithoutDeliveringLateText() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        val qwen = config.copy(baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", model = "qwen3.8-flash")
        val body = object : ResponseBody() {
            override fun contentType() = "text/event-stream".toMediaType()
            override fun contentLength() = -1L
            override fun source() = object : ForwardingSource(Buffer().writeUtf8(
                "data: {\"choices\":[{\"delta\":{\"content\":\"late\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
            )) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    entered.complete(Unit)
                    check(release.await(2, TimeUnit.SECONDS))
                    return super.read(sink, byteCount)
                }
            }.buffer()
        }
        val client = AiClient.defaultClient(3).newBuilder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.set(true) }
        }).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body).build()
        }.build()
        try {
            val task = async { AiClient(qwen, client).complete("规则", "文".repeat(800), WorkScene.MINUTES) }
            withTimeout(2_000) { entered.await() }
            task.cancelAndJoin()
            assertTrue(cancelled.get())
            assertTrue(task.isCancelled)
        } finally {
            release.countDown()
            client.dispatcher.executorService.shutdown()
            client.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}
