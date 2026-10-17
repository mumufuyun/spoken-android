/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AndroidVoiceAudioCapture : VoiceAudioCapture {
    private var recorder: AudioRecord? = null
    private var readJob: Job? = null
    private val acceptingAudio = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    override suspend fun start(scope: CoroutineScope, onFrame: (ByteArray) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            stop()
            var lastError: Throwable? = null
            repeat(START_DELAYS_MS.size) { attempt ->
                if (attempt > 0) delay(START_DELAYS_MS[attempt])
                currentCoroutineContext().ensureActive()
                var created: AudioRecord? = null
                val candidate = runCatching {
                    val minBuffer = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    check(minBuffer > 0) { "AudioRecord buffer is unavailable" }
                    val recorder = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuffer * 2, FRAME_BYTES * 4)
                    ).also {
                        created = it
                        check(it.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
                        it.startRecording()
                        check(it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            "Microphone did not enter recording state"
                        }
                    }
                    recorder
                }
                candidate.onSuccess { active ->
                    recorder = active
                    acceptingAudio.set(true)
                    readJob = scope.launch(Dispatchers.IO) {
                        val buffer = ByteArray(FRAME_BYTES)
                        while (isActive && acceptingAudio.get()) {
                            val read = active.read(buffer, 0, buffer.size)
                            if (read > 0 && acceptingAudio.get()) onFrame(buffer.copyOf(read))
                        }
                    }
                    return@withContext Result.success(Unit)
                }.onFailure {
                    runCatching { created?.release() }
                    if (it is CancellationException) throw it
                    lastError = it
                }
            }
            Result.failure(lastError ?: IllegalStateException("Microphone start failed"))
        }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        acceptingAudio.set(false)
        val active = recorder
        recorder = null
        runCatching { active?.stop() }
        val reading = readJob
        readJob = null
        reading?.cancelAndJoin()
        runCatching { active?.release() }
        Unit
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_BYTES = 3_200
        private val START_DELAYS_MS = longArrayOf(0, 150, 350, 800)
    }
}
