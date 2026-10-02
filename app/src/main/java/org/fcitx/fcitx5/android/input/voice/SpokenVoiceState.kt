/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

sealed interface SpokenVoiceState {
    data object Idle : SpokenVoiceState
    data class Starting(val message: String) : SpokenVoiceState
    data class Recording(val transcript: String) : SpokenVoiceState
    data object Finishing : SpokenVoiceState
    data object AiProcessing : SpokenVoiceState
    data class Notice(val message: String) : SpokenVoiceState
    data class Error(val message: String) : SpokenVoiceState
}

val SpokenVoiceState.isActive: Boolean
    get() = this is SpokenVoiceState.Starting ||
        this is SpokenVoiceState.Recording ||
        this is SpokenVoiceState.Finishing ||
        this is SpokenVoiceState.AiProcessing

/** Voice-key tap semantics per state. */
internal enum class VoiceKeyTapAction { START, STOP, SKIP_AI }

internal fun voiceKeyTapAction(state: SpokenVoiceState): VoiceKeyTapAction = when {
    // During AI processing the key stays live: a tap skips AI and commits the raw transcript.
    state is SpokenVoiceState.AiProcessing -> VoiceKeyTapAction.SKIP_AI
    state.isActive -> VoiceKeyTapAction.STOP
    else -> VoiceKeyTapAction.START
}

internal object SpokenSpeechPostProcessor {
    // Preserve ambiguous names (for example, 杰森). Only join explicitly spaced capitals;
    // lexical correction belongs to an AI scene with context, never the direct-input path.
    private val acronym = Regex("(?<![A-Za-z0-9])(?:[A-Z][ \t]+){2,}[A-Z](?![A-Za-z0-9])")

    fun process(text: String): String = text.trim().replace(acronym) { match ->
        match.value.filterNot(Char::isWhitespace)
    }
}
