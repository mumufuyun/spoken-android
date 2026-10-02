/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val client = FakeClient(result = "整理后的文字")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals(
            "整理后的文字",
            processor.process("口语输入", WorkScene.WORK, OutputLanguage.ORIGINAL).text
        )
        assertEquals(1, client.calls)
        assertEquals(Prompts.system(WorkScene.WORK, OutputLanguage.ORIGINAL), client.lastSystemPrompt)
    }

    @Test
    fun directOriginalBypassesClient() = runBlocking {
        val client = FakeClient(result = "x")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("原文", processor.process("原文", WorkScene.DIRECT, OutputLanguage.ORIGINAL).text)
        assertEquals(0, client.calls)
    }

    @Test
    fun directWithLanguageIsPureTranslation() = runBlocking {
        val client = FakeClient(result = "hello")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("hello", processor.process("原文", WorkScene.DIRECT, OutputLanguage.ENGLISH).text)
        assertEquals(1, client.calls)
        assertEquals(Prompts.system(WorkScene.DIRECT, OutputLanguage.ENGLISH), client.lastSystemPrompt)
        assertTrue(client.lastSystemPrompt!!.contains("翻译"))
    }

    @Test
    fun disabledBypassesClient() = runBlocking {
        val client = FakeClient(result = "x")
        val processor = AiProcessor(enabled = false, client = client)
        assertEquals("原文", processor.process("原文", WorkScene.CHAT, OutputLanguage.ORIGINAL).text)
        assertEquals(0, client.calls)
    }

    @Test
    fun failureFallsBackToTranscript() = runBlocking {
        val client = FakeClient(failure = RuntimeException("boom"))
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("识别原文", processor.process("识别原文", WorkScene.SHARE, OutputLanguage.ENGLISH).text)
    }

    @Test
    fun blankResultFallsBackToTranscript() = runBlocking {
        val client = FakeClient(result = "  ")
        val processor = AiProcessor(enabled = true, client = client)
        assertEquals("识别原文", processor.process("识别原文", WorkScene.MINUTES, OutputLanguage.ORIGINAL).text)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancellationPropagatesInsteadOfFallingBack() = runBlocking<Unit> {
        val client = FakeClient(failure = kotlinx.coroutines.CancellationException("skip"))
        val processor = AiProcessor(enabled = true, client = client)
        processor.process("识别原文", WorkScene.CHAT, OutputLanguage.ORIGINAL)
    }

    @Test
    fun smoothOriginalCallsAiInsteadOfChangingDirectMode() = runBlocking {
        val client = FakeClient(result = "明天见。")
        val result = AiProcessor(true, client).process("嗯明天见", WorkScene.SMOOTH, OutputLanguage.ORIGINAL)
        assertEquals("明天见。", result.text)
        assertNull(result.fallback)
        assertEquals(1, client.calls)
    }

    @Test
    fun failedAndUnsafeResponsesRetainTranscriptWithExplicitOutcome() = runBlocking {
        val original = "杰森说可能周五完成"
        val unsafe = AiProcessor(true, FakeClient("<think>private</think>周五完成"))
            .process(original, WorkScene.WORK, OutputLanguage.ORIGINAL)
        assertEquals(original, unsafe.text)
        assertEquals(AiFallbackReason.UNSAFE_OUTPUT, unsafe.fallback)
        val failed = AiProcessor(true, FakeClient(failure = java.io.IOException("failure")))
            .process(original, WorkScene.WORK, OutputLanguage.ORIGINAL)
        assertEquals(original, failed.text)
        assertEquals(AiFallbackReason.REQUEST_FAILED, failed.fallback)
        val empty = AiProcessor(true, FakeClient(" "))
            .process(original, WorkScene.WORK, OutputLanguage.ORIGINAL)
        assertEquals(AiFallbackReason.EMPTY_RESPONSE, empty.fallback)
    }
}
