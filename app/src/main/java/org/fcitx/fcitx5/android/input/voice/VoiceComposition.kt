/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/** Owns only one recording's temporary text. Host edits invalidate ownership before any write. */
class VoiceComposition(private val editor: Editor) {
    data class Snapshot(val text: String, val offset: Int, val selectionStart: Int, val selectionEnd: Int)

    interface Editor {
        fun snapshot(): Snapshot?
        fun compose(text: String): Boolean
        fun commit(text: String): Boolean
        fun finish()
        fun select(start: Int, end: Int)
    }

    private val initial = editor.snapshot()
    private var expected = initial
    private var live = true
    private var preview = ""
    val hasPreview get() = preview.isNotEmpty()
    val start get() = initial?.let { minOf(it.selectionStart, it.selectionEnd) } ?: -1
    val end get() = start + preview.length
    var inlineAvailable = initial?.let {
        start >= it.offset && maxOf(it.selectionStart, it.selectionEnd) <= it.offset + it.text.length
    } == true
        private set

    fun preview(text: String): Boolean {
        if (!live || !inlineAvailable || text.isEmpty()) return false
        if (!unchanged()) return false
        val before = expected ?: return false
        if (!editor.compose(text)) {
            inlineAvailable = false
            return false
        }
        expected = replacement(before, text)
        preview = text
        return true
    }

    fun commit(text: String): Boolean {
        if (!live || !unchanged()) return false
        val accepted = editor.commit(text)
        if (!accepted) editor.finish()
        live = false
        return accepted
    }

    /** Explicit cancel restores a replaced selection; it never deletes by guessed cursor offsets. */
    fun cancel() {
        if (!live) return
        if (hasPreview && unchanged()) {
            val source = initial!!
            val original = source.text.substring(start - source.offset, maxOf(source.selectionStart, source.selectionEnd) - source.offset)
            if (editor.commit(original)) editor.select(source.selectionStart, source.selectionEnd)
            else editor.finish()
        } else if (hasPreview) {
            editor.finish()
        }
        live = false
    }

    fun abandon(finish: Boolean = true) {
        if (live && hasPreview && finish) editor.finish()
        live = false
    }

    fun unchanged(): Boolean {
        if (!live) return false
        // Editors without extraction support use the keyboard preview, guarded by selection events.
        val value = expected ?: return true
        val current = editor.snapshot() ?: return false
        return current == value
    }

    private fun replacement(before: Snapshot, text: String): Snapshot {
        val replaceEnd = if (hasPreview) end else maxOf(before.selectionStart, before.selectionEnd)
        val content = before.text.replaceRange(start - before.offset, replaceEnd - before.offset, text)
        return Snapshot(content, before.offset, start + text.length, start + text.length)
    }
}
