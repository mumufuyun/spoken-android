/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Xunfei IAT realtime recognition over WebSocket. Audio capture is owned by the caller;
 * this provider only turns PCM frames into IAT protocol frames.
 *
 * Behavior contract: streaming partials while recording; on a mid-session connection loss
 * the socket reconnects with bounded retries (300ms, 800ms), the full session audio is
 * replayed into the new connection (first frame carries common/business params again), and
 * the accumulated transcript is preserved. Once finishing, any connection loss completes
 * immediately with the accumulated transcript, which may be empty (the coordinator then
 * reports that no speech was detected) instead of waiting for the finish timeout.
 */
class XunfeiAsrProvider(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val listener: SpokenAsrProvider.Listener,
    private val client: OkHttpClient = defaultClient()
) : SpokenAsrProvider {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SpokenXunfeiSession")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var sessionId: UUID? = null
    private var socket: WebSocket? = null
    private var open = false
    private var firstFrameSent = false
    private var finishing = false
    private var connectTimeout: Job? = null
    private var finishTimeout: Job? = null
    private var reconnectAttempt = 0
    private val transcript = StringBuilder()
    private val pendingFrames = ArrayDeque<ByteArray>()
    private var pendingBytes = 0
    // Frames already sent; replayed in order after a reconnect because each WebSocket
    // connection is a fresh IAT session.
    private val sentFrames = ArrayDeque<ByteArray>()
    private var sentBytes = 0

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
            if (!open) {
                pendingFrames.addLast(bytes.copyOf())
                pendingBytes += bytes.size
                while (pendingBytes > MAX_PENDING_BYTES && pendingFrames.isNotEmpty()) {
                    pendingBytes -= pendingFrames.removeFirst().size
                }
            } else {
                sendPcm(bytes)
            }
        }
    }

    override fun finish() {
        scope.launch {
            if (sessionId == null || finishing) return@launch
            finishing = true
            if (open) sendEndFrame()
            finishTimeout?.cancel()
            finishTimeout = scope.launch {
                delay(FINISH_TIMEOUT_MS)
                completeWithTranscript()
            }
        }
    }

    override fun cancel() {
        scope.launch { resetSession() }
    }

    override fun close() {
        sessionId = null
        socket?.cancel()
        socket = null
        scope.cancel()
        dispatcher.close()
    }

    private fun connect(id: UUID) {
        open = false
        val request = Request.Builder()
            .url(XunfeiIatProtocol.buildSignedUrl(apiKey, apiSecret))
            .build()
        lateinit var created: WebSocket
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = dispatch(webSocket, id) {
                connectTimeout?.cancel()
                open = true
                reconnectAttempt = 0
                // A reconnect opens a fresh IAT session: resend the first-frame params.
                firstFrameSent = false
                sentFrames.forEach { sendPcm(it, record = false) }
                while (pendingFrames.isNotEmpty()) {
                    val frame = pendingFrames.removeFirst()
                    pendingBytes -= frame.size
                    sendPcm(frame)
                }
                if (finishing) sendEndFrame() else listener.onReady()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = dispatch(webSocket, id) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                dispatch(webSocket, id) {
                    handleConnectionFailure(webSocket, id, "语音连接已断开")
                }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                dispatch(webSocket, id) {
                    handleConnectionFailure(webSocket, id, t.message ?: "语音连接失败")
                }
        }
        created = client.newWebSocket(request, wsListener)
        socket = created
        connectTimeout?.cancel()
        connectTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!open) handleConnectionFailure(created, id, "连接语音服务超时")
        }
    }

    private fun dispatch(webSocket: WebSocket, id: UUID, block: suspend () -> Unit) {
        scope.launch {
            if (sessionId != id || socket !== webSocket) return@launch
            block()
        }
    }

    private fun sendPcm(bytes: ByteArray, record: Boolean = true) {
        // Xunfei IAT expects at most ~40ms (1280 bytes at 16kHz/16bit/mono) per frame.
        bytes.asIterable().chunked(FRAME_BYTES).forEach { chunk ->
            val frame = chunk.toByteArray()
            if (record) {
                sentFrames.addLast(frame)
                sentBytes += frame.size
                while (sentBytes > MAX_PENDING_BYTES && sentFrames.isNotEmpty()) {
                    sentBytes -= sentFrames.removeFirst().size
                }
            }
            val status = if (firstFrameSent) {
                XunfeiIatProtocol.STATUS_CONTINUE
            } else {
                firstFrameSent = true
                XunfeiIatProtocol.STATUS_FIRST
            }
            socket?.send(XunfeiIatProtocol.buildAudioFrame(appId, frame, status))
        }
    }

    private fun sendEndFrame() {
        socket?.send(
            XunfeiIatProtocol.buildAudioFrame(appId, ByteArray(0), XunfeiIatProtocol.STATUS_LAST)
        )
    }

    private fun handleConnectionFailure(webSocket: WebSocket, id: UUID, message: String) {
        if (sessionId != id || socket !== webSocket) return
        webSocket.cancel()
        socket = null
        open = false
        when (failureAction(finishing, reconnectAttempt)) {
            FailureAction.COMPLETE -> completeWithTranscript()
            FailureAction.RETRY -> {
                val delayMs = RECONNECT_DELAYS_MS[reconnectAttempt++]
                Timber.w("Spoken Xunfei ASR reconnect attempt=%d delayMs=%d", reconnectAttempt, delayMs)
                scope.launch {
                    delay(delayMs)
                    if (sessionId == id) connect(id)
                }
            }
            FailureAction.FAIL -> fail(message)
        }
    }

    private fun handleMessage(raw: String) {
        val message = XunfeiIatProtocol.parseMessage(raw) ?: return
        if (message.code != 0) {
            fail("讯飞识别失败 (${message.code}): ${message.message ?: "未知错误"}")
            return
        }
        if (message.text.isNotEmpty()) {
            transcript.append(message.text)
            if (!message.isFinal) listener.onPartial(transcript.toString())
        }
        if (message.isFinal) completeWithTranscript()
    }

    private fun completeWithTranscript() {
        val result = transcript.toString()
        resetSession()
        listener.onFinal(result)
    }

    private fun fail(message: String) {
        Timber.e("Spoken Xunfei ASR failed: %s", message)
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
        open = false
        firstFrameSent = false
        finishing = false
        reconnectAttempt = 0
        transcript.setLength(0)
        pendingFrames.clear()
        pendingBytes = 0
        sentFrames.clear()
        sentBytes = 0
    }

    companion object {
        private const val FRAME_BYTES = 1_280
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val FINISH_TIMEOUT_MS = 8_000L
        private const val MAX_PENDING_BYTES = 16 * 1024 * 1024
        private val RECONNECT_DELAYS_MS = longArrayOf(300, 800)

        internal enum class FailureAction { RETRY, COMPLETE, FAIL }

        /**
         * Mid-session loss retries with backoff; once finishing, complete immediately with the
         * accumulated transcript (possibly empty) instead of retrying or waiting out the
         * finish timeout; retries exhausted mid-session is a hard failure.
         */
        internal fun failureAction(finishing: Boolean, reconnectAttempt: Int): FailureAction = when {
            finishing -> FailureAction.COMPLETE
            reconnectAttempt < RECONNECT_DELAYS_MS.size -> FailureAction.RETRY
            else -> FailureAction.FAIL
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
