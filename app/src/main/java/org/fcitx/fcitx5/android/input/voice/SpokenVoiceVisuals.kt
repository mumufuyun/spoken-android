/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import androidx.annotation.ColorInt

/**
 * Maps [SpokenVoiceState] to Flat Pop visual semantics (design mockup 15): flat solid colors
 * only — no gradients, shadows or glow. Pure logic so it is unit-testable.
 */
object SpokenVoiceVisuals {
    const val COLOR_WARNING = 0xFFFF9500.toInt()
    const val COLOR_ERROR = 0xFFFF3B30.toInt()

    /** Finishing (recognizing) and AI processing keep the semantic blue/orange solids. */
    const val COLOR_RECOGNIZING = 0xFF4A90D9.toInt()
    const val COLOR_AI_PROCESSING = 0xFFF39C12.toInt()

    /** Status green from the mockup tokens: #2BAE5F light, #3CCB74 dark. */
    @ColorInt
    fun readyColor(dark: Boolean): Int = if (dark) 0xFF3CCB74.toInt() else 0xFF2BAE5F.toInt()

    /** Status dot: warning while connecting, ready green while recording, accent while processing. */
    @ColorInt
    fun statusDotColor(state: SpokenVoiceState, @ColorInt accentColor: Int, dark: Boolean): Int =
        when (state) {
            is SpokenVoiceState.Starting -> COLOR_WARNING
            is SpokenVoiceState.Recording -> readyColor(dark)
            SpokenVoiceState.Finishing, SpokenVoiceState.AiProcessing -> accentColor
            is SpokenVoiceState.Error -> COLOR_ERROR
            is SpokenVoiceState.Notice -> COLOR_WARNING
            SpokenVoiceState.Idle -> accentColor
        }

    /** Solid waveform color for [state] (recording uses the theme accent); null hides the wave. */
    @ColorInt
    fun waveformColor(state: SpokenVoiceState, @ColorInt accentColor: Int): Int? = when (state) {
        is SpokenVoiceState.Recording -> accentColor
        SpokenVoiceState.Finishing -> COLOR_RECOGNIZING
        SpokenVoiceState.AiProcessing -> COLOR_AI_PROCESSING
        SpokenVoiceState.Idle, is SpokenVoiceState.Starting, is SpokenVoiceState.Error,
        is SpokenVoiceState.Notice -> null
    }
}
