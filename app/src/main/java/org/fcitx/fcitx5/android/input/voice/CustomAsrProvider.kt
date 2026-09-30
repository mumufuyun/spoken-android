/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import timber.log.Timber

/** Wraps 16kHz/mono/16bit PCM in a canonical RIFF/WAVE header. */
internal object WavEncoder {
    fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(44 + pcm.size)

        fun Int.le4() = byteArrayOf(toByte(), shr(8).toByte(), shr(16).toByte(), shr(24).toByte())
        fun Int.le2() = byteArrayOf(toByte(), shr(8).toByte())

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write((36 + pcm.size).le4())
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(16.le4())
        out.write(1.le2())
        out.write(channels.le2())
        out.write(sampleRate.le4())
        out.write(byteRate.le4())
        out.write(blockAlign.le2())
        out.write(bitsPerSample.le2())
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(pcm.size.le4())
        out.write(pcm)
        return out.toByteArray()
    }
}

/**
 * Batch ASR against an OpenAI Whisper-compatible endpoint: PCM frames are buffered and
 * uploaded as a single multipart WAV on [finish]. There are no streaming partials and no
 * retry — a failed upload surfaces as an error after the recording stops.
 */
class CustomAsrProvider(
    private val endpointUrl: String,
    private val apiKey: String,
    private val model: String,
    private val listener: SpokenAsrProvider.Listener,
    private val client: OkHttpClient = OkHttpClient()
) : SpokenAsrProvider {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SpokenCustomSession")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val pcmBuffer = ByteArrayOutputStream()
    private var active = false

    override fun start() {
        scope.launch {
            pcmBuffer.reset()
            active = true
            listener.onReady()
        }
    }

    override fun appendAudio(bytes: ByteArray) {
        scope.launch {
            if (!active) return@launch
            pcmBuffer.write(bytes)
        }
    }

    override fun finish() {
        scope.launch {
            if (!active) return@launch
            active = false
            try {
                val wav = WavEncoder.pcmToWav(pcmBuffer.toByteArray())
                listener.onFinal(upload(wav))
            } catch (e: Exception) {
                Timber.e(e, "Spoken custom ASR failed")
                listener.onError(e.message ?: "语音识别失败")
            }
        }
    }

    override fun cancel() {
        scope.launch {
            active = false
            pcmBuffer.reset()
        }
    }

    override fun close() {
        active = false
        scope.cancel()
        dispatcher.close()
    }

    private fun upload(wav: ByteArray): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(endpointUrl)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ASR HTTP ${response.code}")
            val raw = response.body?.string().orEmpty()
            return runCatching {
                json.parseToJsonElement(raw).jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: raw.trim()
        }
    }
}
