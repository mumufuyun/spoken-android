/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenVoiceVisualsTest {
    private val accent = 0xFFFF6B4A.toInt()

    @Test
    fun waveformShowsDuringRecordingFinishingAndAiProcessing() {
        assertEquals(
            accent,
            SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Recording("hi"), accent)
        )
        assertEquals(
            SpokenVoiceVisuals.COLOR_RECOGNIZING,
            SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Finishing, accent)
        )
        assertEquals(
            SpokenVoiceVisuals.COLOR_AI_PROCESSING,
            SpokenVoiceVisuals.waveformColor(SpokenVoiceState.AiProcessing, accent)
        )
    }

    @Test
    fun waveformHiddenOtherwise() {
        assertNull(SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Idle, accent))
        assertNull(SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Notice("原文已输入"), accent))
        assertEquals(SpokenVoiceVisuals.COLOR_WARNING,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Notice("原文已输入"), accent, false))
        assertNull(SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Starting("…"), accent))
        assertNull(SpokenVoiceVisuals.waveformColor(SpokenVoiceState.Error("x"), accent))
    }

    @Test
    fun statusDotFollowsSemantics() {
        assertEquals(
            SpokenVoiceVisuals.COLOR_WARNING,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Starting("…"), accent, dark = false)
        )
        assertEquals(
            SpokenVoiceVisuals.readyColor(dark = false),
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Recording("hi"), accent, dark = false)
        )
        assertEquals(
            SpokenVoiceVisuals.readyColor(dark = true),
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Recording("hi"), accent, dark = true)
        )
        assertEquals(
            accent,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.AiProcessing, accent, dark = false)
        )
        assertEquals(
            SpokenVoiceVisuals.COLOR_ERROR,
            SpokenVoiceVisuals.statusDotColor(SpokenVoiceState.Error("x"), accent, dark = false)
        )
    }

    @Test
    fun readyGreenFollowsThemeBrightness() {
        assertEquals(0xFF2BAE5F.toInt(), SpokenVoiceVisuals.readyColor(dark = false))
        assertEquals(0xFF3CCB74.toInt(), SpokenVoiceVisuals.readyColor(dark = true))
    }
}
