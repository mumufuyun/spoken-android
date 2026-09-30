/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VoiceAudioCapture {
    suspend fun start(scope: CoroutineScope, onFrame: (ByteArray) -> Unit): Result<Unit>
    suspend fun stop()
}

/** One recording's ownership boundary, including microphone startup and terminal cleanup. */
internal class VoiceAudioSession(
    private val scope: CoroutineScope,
    private val capture: VoiceAudioCapture
) {
    private var startJob: Job? = null
    private var stopJob: Job? = null
    @Volatile private var stopped = false

    fun start(onFrame: (ByteArray) -> Unit, onFailure: () -> Unit) {
        check(startJob == null && !stopped) { "A recording session cannot be reused" }
        startJob = scope.launch(start = CoroutineStart.LAZY) {
            val result = capture.start(scope) { frame ->
                if (!stopped) onFrame(frame)
            }
            if (!stopped && result.isFailure) onFailure()
        }
        startJob!!.start()
    }

    fun stop(): Job {
        stopJob?.let { return it }
        stopped = true
        val starting = startJob
        starting?.cancel()
        // Join startup before releasing: cancellation may race a successful AudioRecord
        // allocation. Cleanup must also survive destruction of the owning IME scope.
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                starting?.join()
                capture.stop()
            }
        }.also { stopJob = it }
    }
}
