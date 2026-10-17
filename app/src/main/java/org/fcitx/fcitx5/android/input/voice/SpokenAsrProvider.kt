/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/**
 * Contract for a streaming (or batch) speech recognition engine. The coordinator owns
 * microphone capture; implementations only handle the network protocol.
 */
interface SpokenAsrProvider {
    fun start()
    fun appendAudio(bytes: ByteArray)
    fun finish()
    fun cancel()
    fun close()

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(transcript: SpokenTranscript)
        fun onError(message: String)
    }
}

/** A timeout or broken connection must not promote a partial transcript to a finished one. */
data class SpokenTranscript(val text: String, val isComplete: Boolean = true) {
    fun allowsAi(scene: WorkScene, language: OutputLanguage): Boolean =
        isComplete && (scene != WorkScene.DIRECT || language != OutputLanguage.ORIGINAL)
}
