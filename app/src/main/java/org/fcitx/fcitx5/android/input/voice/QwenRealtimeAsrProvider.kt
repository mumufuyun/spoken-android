/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Qwen realtime ASR over the DashScope WebSocket API.
 *
 * Behavior contract: streaming partials while recording; bounded reconnect (300ms, 800ms)
 * with audio replay on mid-session loss; once finishing, the latest transcript completes
 * the session.
 */
class QwenRealtimeAsrProvider(
    private val apiKey: String,
    private val listener: SpokenAsrProvider.Listener,
    private val client: OkHttpClient = defaultClient()
) : SpokenAsrProvider {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SpokenQwenSession")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private var sessionId: UUID? = null
    private var socket: WebSocket? = null
    private var ready = false
    private var finishing = false
    private var reconnectAttempt = 0
    private var latestTranscript = ""
    private var connectTimeout: Job? = null
    private var finishTimeout: Job? = null
    private val replayFrames = ArrayDeque<ByteArray>()
    private var replayBytes = 0

    override fun start() {
        scope.launch {
            resetSession()
            sessionId = UUID.randomUUID()
            connect(sessionId!!)
        }
    }

    override fun appendAudio(bytes: ByteArray) {
        scope.launch {
            if (sessionId == null || finishing) return@launch
            rememberForReplay(bytes)
            if (ready) sendAudio(bytes)
        }
    }

    override fun finish() {
        scope.launch {
            if (sessionId == null || finishing) return@launch
            finishing = true
            if (ready) sendEvent("input_audio_buffer.commit")
            finishTimeout?.cancel()
            finishTimeout = scope.launch {
                delay(FINISH_TIMEOUT_MS)
                completeWithLatest()
            }
        }
    }

    override fun cancel() {
        scope.launch { resetSession() }
    }

    override fun close() {
        // close() is the terminal boundary. Cancel the transport synchronously so a scope
        // cancellation cannot strand a live microphone WebSocket behind the IME lifecycle.
        sessionId = null
        socket?.cancel()
        socket = null
        scope.cancel()
        dispatcher.close()
    }

    private fun connect(id: UUID) {
        ready = false
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .build()
        lateinit var created: WebSocket
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = dispatch(webSocket, id) {
                connectTimeout?.cancel()
                connectTimeout = scope.launch {
                    delay(CONNECT_TIMEOUT_MS)
                    if (!ready) handleConnectionFailure(webSocket, id, "连接语音服务超时")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) = dispatch(webSocket, id) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) =
                onMessage(webSocket, bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                dispatch(webSocket, id) {
                    if (!finishing) handleConnectionFailure(webSocket, id, "语音连接已断开")
                }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                dispatch(webSocket, id) {
                    handleConnectionFailure(webSocket, id, t.message ?: "语音连接失败")
                }
        }
        created = client.newWebSocket(request, wsListener)
        socket = created
    }

    private fun dispatch(webSocket: WebSocket, id: UUID, block: suspend () -> Unit) {
        scope.launch {
            if (sessionId != id || socket !== webSocket) return@launch
            block()
        }
    }

    private fun handleMessage(raw: String) {
        val event = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (event.string("type")) {
            "session.created" -> sendSessionUpdate()
            "session.updated" -> {
                connectTimeout?.cancel()
                reconnectAttempt = 0
                ready = true
                replayFrames.forEach(::sendAudio)
                if (finishing) sendEvent("input_audio_buffer.commit") else listener.onReady()
            }
            "conversation.item.input_audio_transcription.text" -> {
                val confirmed = event.string("text").orEmpty()
                val stash = event.string("stash").orEmpty()
                publishText(confirmed + stash)
            }
            "conversation.item.input_audio_transcription.delta" -> {
                event.string("delta")?.takeIf(String::isNotEmpty)?.let { delta ->
                    publishText(latestTranscript + delta)
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                extractTranscript(event)?.let(::publishText)
                if (finishing) sendEvent("session.finish")
            }
            "session.finished" -> {
                completeWithLatest()
            }
            "error" -> {
                val message = event["error"]?.jsonObject?.string("message")
                    ?: event.string("message")
                    ?: "语音识别失败"
                fail(message)
            }
        }
    }

    private fun publishText(transcript: String) {
        if (transcript.isBlank()) return
        latestTranscript = transcript
        listener.onPartial(transcript)
    }

    private fun extractTranscript(event: JsonObject): String? {
        event.string("transcript")?.let { return it }
        return runCatching {
            event["item"]?.jsonObject
                ?.get("content")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.string("transcript")
        }.getOrNull()
    }

    private fun sendSessionUpdate() {
        val event = buildJsonObject {
            put("event_id", eventId())
            put("type", "session.update")
            put("session", buildJsonObject {
                put("modalities", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive("text")) })
                put("input_audio_format", "pcm")
                put("sample_rate", AndroidVoiceAudioCapture.SAMPLE_RATE)
                put("input_audio_transcription", buildJsonObject {
                    put("language", "zh")
                })
                put("turn_detection", null)
            })
        }
        socket?.send(event.toString())
    }

    private fun sendAudio(bytes: ByteArray) {
        val event = buildJsonObject {
            put("event_id", eventId())
            put("type", "input_audio_buffer.append")
            put("audio", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        socket?.send(event.toString())
    }

    private fun sendEvent(type: String) {
        socket?.send(buildJsonObject {
            put("event_id", eventId())
            put("type", type)
        }.toString())
    }

    private fun rememberForReplay(bytes: ByteArray) {
        replayFrames.addLast(bytes.copyOf())
        replayBytes += bytes.size
        while (replayBytes > MAX_REPLAY_BYTES && replayFrames.isNotEmpty()) {
            replayBytes -= replayFrames.removeFirst().size
        }
    }

    private fun handleConnectionFailure(webSocket: WebSocket, id: UUID, message: String) {
        if (sessionId != id || socket !== webSocket) return
        webSocket.cancel()
        ready = false
        if (reconnectAttempt < RECONNECT_DELAYS_MS.size) {
            val delayMs = RECONNECT_DELAYS_MS[reconnectAttempt++]
            Timber.w("Spoken ASR reconnect attempt=%d delayMs=%d", reconnectAttempt, delayMs)
            scope.launch {
                delay(delayMs)
                if (sessionId == id) connect(id)
            }
        } else if (finishing && latestTranscript.isNotBlank()) {
            completeWithLatest()
        } else {
            fail(message)
        }
    }

    private fun completeWithLatest() {
        val result = latestTranscript
        resetSession()
        listener.onFinal(result)
    }

    private fun fail(message: String) {
        Timber.e("Spoken ASR failed: %s", message)
        resetSession()
        listener.onError(message)
    }

    private fun resetSession() {
        connectTimeout?.cancel()
        finishTimeout?.cancel()
        connectTimeout = null
        finishTimeout = null
        socket?.cancel()
        socket = null
        sessionId = null
        ready = false
        finishing = false
        reconnectAttempt = 0
        latestTranscript = ""
        replayFrames.clear()
        replayBytes = 0
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull

    private fun eventId(): String = "event_${UUID.randomUUID()}"

    companion object {
        private const val ENDPOINT =
            "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime"
        private const val MODEL = "qwen3-asr-flash-realtime"
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val FINISH_TIMEOUT_MS = 8_000L
        private const val MAX_REPLAY_BYTES = 16 * 1024 * 1024
        private val RECONNECT_DELAYS_MS = longArrayOf(300, 800)

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
