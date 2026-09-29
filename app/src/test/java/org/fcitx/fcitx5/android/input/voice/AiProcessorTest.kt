/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProcessorTest {
    private class FakeClient(
        var result: String = "",
        var failure: Throwable? = null
    ) : AiCompletionClient {
        var calls = 0
        var lastSystemPrompt: String? = null

        override suspend fun complete(systemPrompt: String, userText: String): String {
            calls++
            lastSystemPrompt = systemPrompt
            failure?.let { throw it }
            return result
        }
    }

    @Test
    fun returnsClientResult() = runBlocking {
        val client = FakeClient(result = "润色后的文字")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("润色后的文字", processor.process("口语输入", WorkMode.POLISH))
        assertEquals(1, client.calls)
        assertEquals(Prompts.system(WorkMode.POLISH), client.lastSystemPrompt)
    }

    @Test
    fun directModeBypassesClient() = runBlocking {
        val client = FakeClient(result = "x")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("原文", processor.process("原文", WorkMode.DIRECT))
        assertEquals(0, client.calls)
    }

    @Test
    fun disabledBypassesClient() = runBlocking {
        val client = FakeClient(result = "x")
        val processor = AiProcessor(enabled = false, client = client)
        assertEquals("原文", processor.process("原文", WorkMode.POLISH))
        assertEquals(0, client.calls)
    }

    @Test
    fun failureFallsBackToTranscript() = runBlocking {
        val client = FakeClient(failure = RuntimeException("boom"))
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("识别原文", processor.process("识别原文", WorkMode.TRANSLATE))
    }

    @Test
    fun blankResultFallsBackToTranscript() = runBlocking {
        val client = FakeClient(result = "  ")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("识别原文", processor.process("识别原文", WorkMode.MINUTES))
    }
}
