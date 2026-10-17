/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceCompositionTest {
    private class Editor(text: String, start: Int, end: Int = start, offset: Int = 0) : VoiceComposition.Editor {
        var value = VoiceComposition.Snapshot(text, offset, start, end)
        var region: IntRange? = null
        var extractable = true
        var composingSupported = true
        var commits = 0
        override fun snapshot() = if (extractable) value else null
        private fun replace(text: String) {
            val start = region?.first ?: minOf(value.selectionStart, value.selectionEnd)
            val end = region?.last?.plus(1) ?: maxOf(value.selectionStart, value.selectionEnd)
            value = value.copy(text = value.text.replaceRange(start - value.offset, end - value.offset, text),
                selectionStart = start + text.length, selectionEnd = start + text.length)
            region = start until start + text.length
        }
        override fun compose(text: String): Boolean {
            if (!composingSupported) return false
            replace(text); return true
        }
        override fun commit(text: String): Boolean {
            replace(text); region = null; commits++; return true
        }
        override fun finish() { region = null }
        override fun select(start: Int, end: Int) { value = value.copy(selectionStart = start, selectionEnd = end) }
    }

    @Test fun partialsAndAiReplaceOnlyTheirOwnedSpan() {
        val editor = Editor("开头结尾", 2)
        val voice = VoiceComposition(editor)
        assertTrue(voice.preview("周五三点"))
        assertTrue(voice.preview("周五三点，不对，四点"))
        assertTrue(voice.commit("周五四点"))
        assertEquals("开头周五四点结尾", editor.value.text)
        assertEquals(1, editor.commits)
        assertNull(editor.region)
        assertFalse(voice.commit("迟到结果"))
    }

    @Test fun cancelRestoresOriginalSelectionIncludingReverseDirection() {
        val editor = Editor("开头旧文字结尾", 5, 2)
        val voice = VoiceComposition(editor)
        voice.preview("新文字")
        voice.cancel()
        assertEquals("开头旧文字结尾", editor.value.text)
        assertEquals(5, editor.value.selectionStart)
        assertEquals(2, editor.value.selectionEnd)
    }

    @Test fun cancelAfterManualEditNeverDeletesUserText() {
        val editor = Editor("开头", 2)
        val voice = VoiceComposition(editor)
        voice.preview("原文")
        editor.value = editor.value.copy(text = "开头改过", selectionStart = 4, selectionEnd = 4)
        voice.cancel()
        assertEquals("开头改过", editor.value.text)
        assertEquals(0, editor.commits)
    }

    @Test fun movedCursorOrChangedTextRejectsDelayedAi() {
        val editor = Editor("前后", 1)
        val voice = VoiceComposition(editor)
        voice.preview("原文")
        editor.select(0, 0)
        assertFalse(voice.commit("整理后"))
        voice.abandon()
        assertEquals("前原文后", editor.value.text)
        assertEquals(0, editor.commits)
    }

    @Test fun hidingOrSwitchingEditorAbandonsLateWrites() {
        val editor = Editor("", 0)
        val voice = VoiceComposition(editor)
        voice.preview("原文")
        voice.abandon(finish = false)
        assertFalse(voice.preview("迟到识别"))
        assertFalse(voice.commit("迟到整理"))
        voice.cancel()
        assertEquals("原文", editor.value.text)
    }

    @Test fun fallbackEditorReceivesOneFinalCommit() {
        val editor = Editor("前后", 1).apply { extractable = false }
        val voice = VoiceComposition(editor)
        assertFalse(voice.preview("预览"))
        assertEquals("前后", editor.value.text)
        assertTrue(voice.commit("结果"))
        assertEquals("前结果后", editor.value.text)
        assertEquals(1, editor.commits)
    }

    @Test fun failedCompositionFallsBackWithoutAppendingPartials() {
        val editor = Editor("前后", 1).apply { composingSupported = false }
        val voice = VoiceComposition(editor)
        assertFalse(voice.preview("预览"))
        assertFalse(voice.inlineAvailable)
        assertTrue(voice.commit("结果"))
        assertEquals("前结果后", editor.value.text)
    }

    @Test fun extractedTextOffsetAndEmojiUseUtf16EditorPositions() {
        val editor = Editor("前😀后", 101, 103, offset = 100)
        val voice = VoiceComposition(editor)
        voice.preview("替换")
        voice.cancel()
        assertEquals("前😀后", editor.value.text)
        assertEquals(101, editor.value.selectionStart)
        assertEquals(103, editor.value.selectionEnd)
    }
}
