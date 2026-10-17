/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class AndroidVoiceEditor(
    private val connection: InputConnection,
    private val predictSelection: (Int, Int) -> Unit
) : VoiceComposition.Editor {
    private var composingStart: Int? = null

    override fun snapshot(): VoiceComposition.Snapshot? {
        val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 32_768
            hintMaxLines = 1_024
        }, 0) ?: return null
        val text = extracted.text?.toString() ?: return null
        if (extracted.partialStartOffset >= 0 || text.length > 32_768) return null
        return VoiceComposition.Snapshot(text, extracted.startOffset,
            extracted.startOffset + extracted.selectionStart, extracted.startOffset + extracted.selectionEnd)
    }

    override fun compose(text: String): Boolean {
        val start = composingStart ?: snapshot()?.let { minOf(it.selectionStart, it.selectionEnd) } ?: return false
        val styled = SpannableString(text).apply {
            setSpan(UnderlineSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        }
        predictSelection(start + text.length, start + text.length)
        val accepted = connection.setComposingText(styled, 1)
        if (accepted) composingStart = start
        return accepted
    }

    override fun commit(text: String): Boolean {
        val start = composingStart ?: snapshot()?.let { minOf(it.selectionStart, it.selectionEnd) }
        if (start != null) predictSelection(start + text.length, start + text.length)
        val accepted = connection.commitText(text, 1)
        composingStart = null
        return accepted
    }

    override fun finish() { connection.finishComposingText(); composingStart = null }
    override fun select(start: Int, end: Int) {
        predictSelection(start, end)
        connection.setSelection(start, end)
    }
}
