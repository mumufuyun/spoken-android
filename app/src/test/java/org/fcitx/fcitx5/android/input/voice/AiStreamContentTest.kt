/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AiStreamContentTest {
    private fun event(delta: String, reason: String = "null") =
        "data: {\"choices\":[{\"delta\":$delta,\"finish_reason\":$reason}]}\n\n"
    private val stop = event("{}", "\"stop\"")
    private val done = "data: [DONE]\n\n"
    private fun parse(value: String) = AiClient.parseStreamContent(Buffer().writeUtf8(value))

    @Test
    fun waitsForCompletionAndKeepsOnlyFinalContent() {
        val stream = ": keepalive\n\n" + event("""{"role":"assistant","content":null}""") +
            event("""{"reasoning_content":"private","content":""}""") +
            event("""{"content":"第一段\n"}""") + event("""{"content":"第二段"}""") + stop +
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":10}}\n\n" + done
        assertEquals("第一段\n第二段", parse(stream))
    }

    @Test
    fun missingCompletionOrTruncatedStreamsNeverDeliverPartialText() {
        val text = event("""{"content":"片段"}""")
        listOf(text, text + stop, text + done, text + event("{}", "\"length\"") + done,
            event("""{"reasoning_content":"private"}""") + stop + done).forEach { assertNull(parse(it)) }
    }

    @Test
    fun rejectsToolsRefusalsErrorsAndContentAfterStop() {
        listOf("""{"tool_calls":[{}],"content":"text"}""", """{"function_call":{}}""",
            """{"refusal":"denied"}""", """{"content":123}""").forEach {
            assertNull(parse(event(it) + stop + done))
        }
        assertNull(parse("data: {\"error\":{\"message\":\"failure\"}}\n\n" + done))
        assertNull(parse(event("""{"content":"a"}""") + stop + event("""{"content":"b"}""") + done))
    }

    @Test
    fun acceptsCrLfAndMultilineDataAndDoneWithoutFinalBlankLine() {
        val text = "event: message\r\ndata: {\"choices\":[{\"delta\":\r\ndata: {\"content\":\"正文\"},\"finish_reason\":\"stop\"}]}\r\n\r\n"
        assertEquals("正文", parse(text + "data: [DONE]\r\n"))
    }
}
