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
        fun onFinal(text: String)
        fun onError(message: String)
    }
}
