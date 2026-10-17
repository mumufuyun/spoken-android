/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiClientJsonTest {
    @Test
    fun requestBodyEscapesQuotesBackslashesAndNewlines() {
        val body = AiClient.buildRequestBody(
            model = "gpt-4o-mini",
            systemPrompt = "输出\"JSON\"\\格式",
            userText = "第一行\n第二行 \"嵌套\" 引号",
            policy = AiRequestPolicy.forInput("https://example.invalid/v1", "gpt-4o-mini", WorkScene.WORK, "原文")
        )
        val root = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        assertEquals("gpt-4o-mini", root["model"]!!.jsonPrimitive.content)
        val messages = root["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("输出\"JSON\"\\格式", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("第一行\n第二行 \"嵌套\" 引号", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun requestBodyOmitsBlankSystemPrompt() {
        val body = AiClient.buildRequestBody("m", "", "hello",
            AiRequestPolicy.forInput("https://example.invalid/v1", "m", WorkScene.WORK, "hello"))
        val root = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        val messages = root["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun parseContentReadsFirstChoice() {
        val raw = """{"choices":[{"message":{"role":"assistant","content":"润色后 \"文本\""}}]}"""
        assertEquals("润色后 \"文本\"", AiClient.parseContent(raw))
    }

    @Test
    fun parseContentUnescapesNewlines() {
        val raw = """{"choices":[{"message":{"content":"a\nb\\c"}}]}"""
        assertEquals("a\nb\\c", AiClient.parseContent(raw))
    }

    @Test
    fun parseContentReturnsNullOnMalformedOrEmpty() {
        assertNull(AiClient.parseContent("not json"))
        assertNull(AiClient.parseContent("""{"choices":[]}"""))
        assertNull(AiClient.parseContent(""))
    }

    @Test
    fun rejectsExplicitTruncationRefusalAndToolCalls() {
        listOf("length", "content_filter", "tool_calls", "function_call").forEach { reason ->
            assertNull(AiClient.parseContent("""{"choices":[{"finish_reason":"$reason","message":{"content":"片段"}}]}"""))
        }
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":"x","refusal":"denied"}}]}"""))
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":"x","tool_calls":[{}]}}]}"""))
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":"x","function_call":{}}}]}"""))
    }

    @Test
    fun readsOnlyFinalTextAndNeverUsesSeparateReasoning() {
        assertEquals("正文", AiClient.parseContent("""{"choices":[{"finish_reason":"stop","message":{"content":"正文","reasoning_content":"private"}}]}"""))
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":null,"reasoning_content":"private"}}]}"""))
        assertEquals("第一段\n第二段", AiClient.parseContent("""{"choices":[{"message":{"content":[{"type":"thinking","text":"private"},{"type":"text","text":"第一段\n"},{"type":"output_text","text":"第二段"}]}}]}"""))
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":[{"type":"unknown","text":"x"}]}}]}"""))
        assertNull(AiClient.parseContent("""{"choices":[{"message":{"content":123}}]}"""))
    }

    @Test
    fun acceptsBaseOrCompleteEndpointWithoutDuplicatingPath() {
        assertEquals("https://example.com/v1/chat/completions", AiClient.chatEndpoint(" https://example.com/v1/ "))
        assertEquals("https://example.com/v1/chat/completions", AiClient.chatEndpoint("https://example.com/v1/chat/completions/"))
        assertEquals("https://example.com:8443/v1/chat/completions", AiClient.chatEndpoint("https://example.com:8443/v1"))
    }

    @Test
    fun rejectsCleartextCredentialsAndQueryParameters() {
        listOf("http://example.com/v1", "http://127.0.0.1:8080/v1", "https://user:secret@example.com/v1",
            "https://example.com/v1?key=secret", "https://example.com/v1#fragment", "not a URL").forEach {
            assertNull(it, AiClient.chatEndpoint(it))
        }
    }
}
