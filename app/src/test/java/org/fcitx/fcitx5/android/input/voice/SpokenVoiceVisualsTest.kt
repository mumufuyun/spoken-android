/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenVoiceVisualsTest {
    private val accent = 0xFF85644B.toInt()

    @Test
    fun waveformShowsDuringRecordingFinishingAndAiProcessing() {
        assertArrayEquals(
            SpokenVoiceVisuals.RECORDING_GRADIENT,
            SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.Recording("hi"))
        )
        assertArrayEquals(
            SpokenVoiceVisuals.FINISHING_GRADIENT,
            SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.Finishing)
        )
        assertArrayEquals(
            SpokenVoiceVisuals.AI_PROCESSING_GRADIENT,
            SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.AiProcessing)
        )
    }

    @Test
    fun waveformHiddenOtherwise() {
        assertNull(SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.Idle))
        assertNull(SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.Starting("…")))
        assertNull(SpokenVoiceVisuals.waveformGradient(SpokenVoiceState.Error("x")))
    }

    @Test
    fun statusDotFollowsSemantics() {
        assertEquals(
            SpokenVoiceVisuals.COLOR_WARNING,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Starting("…"), accent)
        )
        assertEquals(
            SpokenVoiceVisuals.COLOR_READY,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Recording("hi"), accent)
        )
        assertEquals(
            accent,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.AiProcessing, accent)
        )
        assertEquals(
            SpokenVoiceVisuals.COLOR_ERROR,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Error("x"), accent)
        )
    }

    @Test
    fun voiceKeyGradientOnlyWhileActive() {
        assertArrayEquals(
            SpokenVoiceVisuals.RECORDING_GRADIENT,
            SpokenVoiceVisuals.voiceKeyGradient(SpokenVoiceState.Recording(""))
        )
        assertArrayEquals(
            SpokenVoiceVisuals.AI_PROCESSING_GRADIENT,
            SpokenVoiceVisuals.voiceKeyGradient(SpokenVoiceState.AiProcessing)
        )
        assertNull(SpokenVoiceVisuals.voiceKeyGradient(SpokenVoiceState.Idle))
        assertNull(SpokenVoiceVisuals.voiceKeyGradient(SpokenVoiceState.Starting("…")))
        assertNull(SpokenVoiceVisuals.voiceKeyGradient(SpokenVoiceState.Error("x")))
    }
}
