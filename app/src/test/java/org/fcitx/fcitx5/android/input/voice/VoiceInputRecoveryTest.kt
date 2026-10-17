/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceInputRecoveryTest {
    private var now = 0L
    private val recovery = VoiceInputRecovery { now }

    @Test
    fun preservesExactOriginalIncludingIncompleteFlag() {
        val original = SpokenTranscript("  杰森说可能周五完成\n", isComplete = false)
        recovery.capture(1, original)
        assertEquals(original, recovery.current(1)?.transcript)
    }

    @Test
    fun expiresAtTwoMinutes() {
        recovery.capture(1, SpokenTranscript("原文"))
        now = VoiceInputRecovery.RETENTION_MS - 1
        assertNotNull(recovery.current(1))
        now++
        assertNull(recovery.current(1))
    }

    @Test
    fun editorChangeClearsOriginalPermanently() {
        recovery.capture(1, SpokenTranscript("私人内容"))
        assertNull(recovery.current(2))
        assertNull(recovery.current(1))
    }

    @Test
    fun newCaptureInvalidatesOldEntryIdentity() {
        val old = recovery.capture(1, SpokenTranscript("旧原文"))!!
        val next = recovery.capture(1, SpokenTranscript("新原文"))!!
        assertNotEquals(old.id, next.id)
        assertEquals(next.id, recovery.current(1)?.id)
    }

    @Test
    fun emptyCancelledRecordingDoesNotOverwritePreviousOriginal() {
        val old = recovery.capture(1, SpokenTranscript("上次原文"))
        assertNull(recovery.capture(1, SpokenTranscript("  ")))
        assertEquals(old, recovery.current(1))
    }

    @Test
    fun oversizedOriginalIsNotSilentlyTruncatedOrReplacedWithOlderText() {
        recovery.capture(1, SpokenTranscript("旧原文"))
        assertNull(recovery.capture(1, SpokenTranscript("字".repeat(VoiceInputRecovery.MAX_CHARACTERS + 1))))
        assertNull(recovery.current(1))
    }

    @Test
    fun clearRemovesRetainedInput() {
        recovery.capture(1, SpokenTranscript("原文"))
        recovery.clear()
        assertNull(recovery.current(1))
    }

    @Test
    fun incompleteRecognitionNeverAllowsAiEvenForTranslation() {
        val partial = SpokenTranscript("我还没说完", isComplete = false)
        WorkScene.entries.forEach { scene ->
            OutputLanguage.entries.forEach { language -> assertFalse(partial.allowsAi(scene, language)) }
        }
        val complete = SpokenTranscript("说完了")
        assertFalse(complete.allowsAi(WorkScene.DIRECT, OutputLanguage.ORIGINAL))
        assertTrue(complete.allowsAi(WorkScene.SMOOTH, OutputLanguage.ORIGINAL))
        assertTrue(complete.allowsAi(WorkScene.DIRECT, OutputLanguage.ENGLISH))
    }
}
