/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import java.util.UUID

/** One short-lived original, never persisted or offered to another input connection. */
internal class VoiceInputRecovery(private val now: () -> Long) {
    data class Entry(
        val id: UUID,
        val editorGeneration: Long,
        val transcript: SpokenTranscript,
        val capturedAt: Long
    )

    private var entry: Entry? = null

    fun capture(generation: Long, transcript: SpokenTranscript): Entry? {
        if (transcript.text.isBlank()) return null
        // Do not truncate a recoverable original: omitting it is safer than silent data loss.
        entry = if (transcript.text.length <= MAX_CHARACTERS) {
            Entry(UUID.randomUUID(), generation, transcript, now())
        } else null
        return entry
    }

    fun current(generation: Long): Entry? {
        val current = entry ?: return null
        if (current.editorGeneration != generation || now() - current.capturedAt >= RETENTION_MS) {
            clear()
            return null
        }
        return current
    }

    fun clear() { entry = null }

    companion object {
        const val RETENTION_MS = 120_000L
        const val MAX_CHARACTERS = 20_000
    }
}
