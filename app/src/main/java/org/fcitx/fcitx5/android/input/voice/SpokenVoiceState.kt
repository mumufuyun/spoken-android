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
    data class Error(val message: String) : SpokenVoiceState
}

val SpokenVoiceState.isActive: Boolean
    get() = this is SpokenVoiceState.Starting ||
        this is SpokenVoiceState.Recording ||
        this is SpokenVoiceState.Finishing ||
        this is SpokenVoiceState.AiProcessing

internal object SpokenSpeechPostProcessor {
    private val replacements = mapOf(
        "阿皮哎" to "API",
        "杰森" to "JSON",
        "优爱" to "UI",
        "优爱克斯" to "UX"
    )

    fun process(text: String): String {
        var result = text.trim()
        replacements.forEach { (spoken, written) -> result = result.replace(spoken, written) }
        return result.replace(Regex("(?i)(?<![a-z])\\s*(?:[a-z]\\s+){2,}[a-z]\\s*(?![a-z])")) { match ->
            match.value.filterNot(Char::isWhitespace).uppercase()
        }
    }
}
