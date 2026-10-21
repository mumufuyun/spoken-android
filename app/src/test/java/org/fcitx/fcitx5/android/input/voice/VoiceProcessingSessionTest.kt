/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class VoiceProcessingSessionTest {
    private data class Delivery(val text: String, val notice: String?, val scene: WorkScene?)
    private class Client : AiCompletionClient {
        val calls = mutableListOf<Pair<WorkScene, String>>()
        val answers = mutableListOf<CompletableDeferred<String>>()
        override suspend fun complete(systemPrompt: String, userText: String, scene: WorkScene): String {
            calls += scene to userText
            val answer = CompletableDeferred<String>().also { answers += it }
            // Model a transport whose response wins a race with cancellation.
            return withContext(NonCancellable) { answer.await() }
        }
    }

    @Test fun latestSceneWinsEvenWhenCancelledResponseArrivesLast() = runBlocking {
        val client = Client()
        val delivered = mutableListOf<Delivery>()
        val session = VoiceProcessingSession(this, "原始口述", WorkScene.CHAT,
            OutputLanguage.ORIGINAL, true, AiProcessor(true, client)) { t, n, s -> delivered += Delivery(t, n, s) }
        session.start(); yield()
        session.selectScene(WorkScene.WORK); yield()
        session.selectScene(WorkScene.FORMAL); yield()
        assertEquals(listOf(WorkScene.CHAT, WorkScene.WORK, WorkScene.FORMAL), client.calls.map { it.first })
        assertTrue(client.calls.all { it.second == "原始口述" })
        client.answers[2].complete("最终公文"); yield(); yield()
        client.answers[0].complete("旧聊天"); client.answers[1].complete("旧工作")
        yield(); yield()
        assertEquals(listOf(Delivery("最终公文", null, WorkScene.FORMAL)), delivered)
        session.selectScene(WorkScene.SHARE)
        assertEquals(3, client.calls.size)
    }

    @Test fun switchingToOriginalDeliversExactAsrOnce() = runBlocking {
        val client = Client()
        val delivered = mutableListOf<Delivery>()
        val original = "  A B C 原文  "
        val session = VoiceProcessingSession(this, original, WorkScene.WORK,
            OutputLanguage.ORIGINAL, true, AiProcessor(true, client)) { t, n, s -> delivered += Delivery(t, n, s) }
        session.start(); yield()
        session.selectScene(WorkScene.DIRECT)
        client.answers.single().complete("过时结果"); yield(); yield()
        assertEquals(listOf(Delivery(original, null, WorkScene.DIRECT)), delivered)
    }

    @Test fun originalWithAnotherLanguageStillUsesTranslation() = runBlocking {
        val client = Client()
        val delivered = mutableListOf<Delivery>()
        val session = VoiceProcessingSession(this, "你好", WorkScene.CHAT,
            OutputLanguage.ENGLISH, true, AiProcessor(true, client)) { t, n, s -> delivered += Delivery(t, n, s) }
        session.selectScene(WorkScene.DIRECT); yield()
        assertEquals(WorkScene.DIRECT, client.calls.single().first)
        client.answers.single().complete("hello"); yield(); yield()
        assertEquals(listOf(Delivery("hello", null, WorkScene.DIRECT)), delivered)
    }

    @Test fun cancellationRejectsLateResultAndDuplicateSelectionDoesNotRestart() = runBlocking {
        val client = Client()
        val delivered = mutableListOf<Delivery>()
        val session = VoiceProcessingSession(this, "原文", WorkScene.WORK,
            OutputLanguage.ORIGINAL, true, AiProcessor(true, client)) { t, n, s -> delivered += Delivery(t, n, s) }
        session.start(); yield()
        session.selectScene(WorkScene.WORK); yield()
        assertEquals(1, client.calls.size)
        session.cancel()
        client.answers.single().complete("旧结果"); yield(); yield()
        assertTrue(delivered.isEmpty())
    }

    @Test fun failedReplacementRetainsExactOriginalWithoutCountingMode() = runBlocking {
        val client = Client()
        val delivered = mutableListOf<Delivery>()
        val session = VoiceProcessingSession(this, "  原文  ", WorkScene.WORK,
            OutputLanguage.ORIGINAL, true, AiProcessor(true, client)) { t, n, s -> delivered += Delivery(t, n, s) }
        session.start(); yield()
        session.selectScene(WorkScene.MINUTES); yield()
        client.answers[1].complete(" "); yield(); yield()
        client.answers[0].complete("旧结果"); yield(); yield()
        assertEquals(1, delivered.size)
        assertEquals("  原文  ", delivered.single().text)
        assertNull(delivered.single().scene)
        assertNotNull(delivered.single().notice)
    }
}
