/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.*
import org.junit.Test

class AiOutputGuardTest {
    @Test
    fun rejectsBalancedNestedAndUnclosedReasoningInsteadOfGuessingFinalText() {
        listOf("<think>private</think>正文", "<thinking><analysis>private</analysis></thinking>正文",
            "<think>还没结束", "正文</think>", "&lt;think&gt;private",
            "<thi\u200Bnk>private</think>").forEach { assertNull(it, AiOutputGuard.clean(it)) }
    }

    @Test
    fun rejectsProtocolEnvelopesAndExplicitInternalHeadings() {
        listOf("<|im_start|>assistant", "{\"reasoning_content\":\"private\",\"content\":\"正文\"}",
            "## 思考过程：\n先分析输入", "Internal reasoning: hidden").forEach {
            assertNull(it, AiOutputGuard.clean(it))
        }
    }

    @Test
    fun preservesOrdinaryAnalysisInstructionsMarkdownAndCode() {
        val body = "## 分析结论\n保留原有步骤和条件。\n```kotlin\nval value = \"分析\"\n```\n- 预计周五完成，尚未确认。"
        assertEquals(body, AiOutputGuard.clean(body))
        assertEquals("帮我分析这段代码。", AiOutputGuard.clean("帮我分析这段代码。"))
    }

    @Test
    fun trimsOnlyOuterWhitespaceAndRejectsEmptyBody() {
        assertEquals("原文\n\n第二段", AiOutputGuard.clean("  原文\n\n第二段  "))
        assertNull(AiOutputGuard.clean(" \n"))
    }
}
