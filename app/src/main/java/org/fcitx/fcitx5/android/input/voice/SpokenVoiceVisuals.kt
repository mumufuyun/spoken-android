/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import androidx.annotation.ColorInt

/**
 * Maps [SpokenVoiceState] to the Spoken desktop visual tokens: semantic status colors and
 * the bottom-to-top waveform/key gradients. Pure logic so it is unit-testable.
 */
object SpokenVoiceVisuals {
    const val COLOR_READY = 0xFF34C759.toInt()
    const val COLOR_WARNING = 0xFFFF9500.toInt()
    const val COLOR_ERROR = 0xFFFF3B30.toInt()

    /** Bottom-to-top gradients per desktop spec. */
    val RECORDING_GRADIENT = intArrayOf(0xFFC0392B.toInt(), 0xFFE74C3C.toInt())
    val FINISHING_GRADIENT = intArrayOf(0xFF4A90D9.toInt(), 0xFF2980B9.toInt())
    val AI_PROCESSING_GRADIENT = intArrayOf(0xFFF39C12.toInt(), 0xFFE67E22.toInt())
    val IDLE_GRADIENT = intArrayOf(0xFF4E4E4E.toInt(), 0xFF6E6E6E.toInt())

    /** Waveform gradient for [state], or null when the waveform should be hidden. */
    fun waveformGradient(state: SpokenVoiceState): IntArray? = when (state) {
        is SpokenVoiceState.Recording -> RECORDING_GRADIENT
        SpokenVoiceState.Finishing -> FINISHING_GRADIENT
        SpokenVoiceState.AiProcessing -> AI_PROCESSING_GRADIENT
        SpokenVoiceState.Idle, is SpokenVoiceState.Starting, is SpokenVoiceState.Error -> null
    }

    /** Status dot: ready green while recording, warning orange while connecting, accent while processing. */
    fun statusDotColor(state: SpokenVoiceState, @ColorInt accentColor: Int): Int = when (state) {
        is SpokenVoiceState.Starting -> COLOR_WARNING
        is SpokenVoiceState.Recording -> COLOR_READY
        SpokenVoiceState.Finishing, SpokenVoiceState.AiProcessing -> accentColor
        is SpokenVoiceState.Error -> COLOR_ERROR
        SpokenVoiceState.Idle -> accentColor
    }

    /**
     * Voice key gradient (bottom-to-top) while a session is active; null means the key keeps
     * its idle inset appearance.
     */
    fun voiceKeyGradient(state: SpokenVoiceState): IntArray? = when (state) {
        is SpokenVoiceState.Recording -> RECORDING_GRADIENT
        SpokenVoiceState.Finishing -> FINISHING_GRADIENT
        SpokenVoiceState.AiProcessing -> AI_PROCESSING_GRADIENT
        SpokenVoiceState.Idle, is SpokenVoiceState.Starting, is SpokenVoiceState.Error -> null
    }
}
