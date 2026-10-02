/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
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
            assertEquals("正文", withTimeout(2_000) { AiClient(config, client).complete("规则", "原文") })
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
            val result = runCatching { withTimeout(2_000) { AiClient(config, client).complete("规则", "原文") } }
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
}
