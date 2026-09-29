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
            userText = "第一行\n第二行 \"嵌套\" 引号"
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
        val body = AiClient.buildRequestBody("m", "", "hello")
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
}
