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
    private val transcript = StringBuilder()
    private val pendingFrames = ArrayDeque<ByteArray>()
    private var pendingBytes = 0

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
        val request = Request.Builder()
            .url(XunfeiIatProtocol.buildSignedUrl(apiKey, apiSecret))
            .build()
        lateinit var created: WebSocket
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = dispatch(webSocket, id) {
                connectTimeout?.cancel()
                open = true
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
                    if (!finishing) fail("语音连接已断开")
                    else if (transcript.isNotBlank()) completeWithTranscript()
                }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                dispatch(webSocket, id) {
                    if (finishing && transcript.isNotBlank()) completeWithTranscript()
                    else fail(t.message ?: "语音连接失败")
                }
        }
        created = client.newWebSocket(request, wsListener)
        socket = created
        connectTimeout?.cancel()
        connectTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!open) fail("连接语音服务超时")
        }
    }

    private fun dispatch(webSocket: WebSocket, id: UUID, block: suspend () -> Unit) {
        scope.launch {
            if (sessionId != id || socket !== webSocket) return@launch
            block()
        }
    }

    private fun sendPcm(bytes: ByteArray) {
        // Xunfei IAT expects at most ~40ms (1280 bytes at 16kHz/16bit/mono) per frame.
        bytes.asIterable().chunked(FRAME_BYTES).forEach { chunk ->
            val status = if (firstFrameSent) {
                XunfeiIatProtocol.STATUS_CONTINUE
            } else {
                firstFrameSent = true
                XunfeiIatProtocol.STATUS_FIRST
            }
            socket?.send(XunfeiIatProtocol.buildAudioFrame(appId, chunk.toByteArray(), status))
        }
    }

    private fun sendEndFrame() {
        socket?.send(
            XunfeiIatProtocol.buildAudioFrame(appId, ByteArray(0), XunfeiIatProtocol.STATUS_LAST)
        )
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
        transcript.setLength(0)
        pendingFrames.clear()
        pendingBytes = 0
    }

    companion object {
        private const val FRAME_BYTES = 1_280
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val FINISH_TIMEOUT_MS = 8_000L
        private const val MAX_PENDING_BYTES = 16 * 1024 * 1024

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
