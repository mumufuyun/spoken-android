/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SpokenVoiceCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val keyStorage: SecureVoiceKeyStorage,
    private val editorGeneration: () -> Long,
    private val editorAllowsVoice: () -> Boolean,
    private val prepareEditor: () -> Unit,
    private val commitIfCurrent: (Long, String) -> Unit,
    private val onState: (SpokenVoiceState) -> Unit
) {
    private data class Session(
        val id: UUID,
        val editorGeneration: Long,
        var providerReady: Boolean = false,
        var firstAudioSeen: Boolean = false
    )

    private val audioCapture = AndroidVoiceAudioCapture()
    private var session: Session? = null
    private var provider: QwenRealtimeAsrProvider? = null
    private var resetJob: Job? = null
    private var state: SpokenVoiceState = SpokenVoiceState.Idle
    private val permissionCallback: (Boolean) -> Unit = { granted ->
        scope.launch(Dispatchers.Main.immediate) {
            if (granted) start() else showError("需要麦克风权限才能使用语音输入")
        }
    }

    val isActive: Boolean
        get() = state.isActive

    fun toggle() {
        if (state.isActive) stop() else start()
    }

    fun start() {
        if (state.isActive) return
        if (!editorAllowsVoice()) {
            showError("当前输入框不允许语音输入")
            return
        }
        val apiKey = keyStorage.loadApiKey()
        if (apiKey.isNullOrBlank()) {
            showError("请先在 Spoken 设置中配置语音 API Key")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VoicePermissionResult.listenOnce(permissionCallback)
            VoicePermissionActivity.launch(context)
            return
        }

        resetJob?.cancel()
        prepareEditor()
        val current = Session(UUID.randomUUID(), editorGeneration())
        session = current
        setState(SpokenVoiceState.Starting("正在连接云端，语音已暂存…"))
        val currentProvider = QwenRealtimeAsrProvider(apiKey, object : QwenRealtimeAsrProvider.Listener {
            override fun onReady() = onSession(current.id) {
                it.providerReady = true
                publishReadyIfPossible(it)
            }

            override fun onPartial(text: String) = onSession(current.id) {
                setState(SpokenVoiceState.Recording(text))
            }

            override fun onFinal(text: String) = onSession(current.id) {
                val generation = it.editorGeneration
                clearSession(closeProvider = true)
                val processed = SpokenSpeechPostProcessor.process(text)
                if (processed.isNotEmpty()) commitIfCurrent(generation, processed)
                setState(SpokenVoiceState.Idle)
            }

            override fun onError(message: String) = onSession(current.id) {
                scope.launch { audioCapture.stop() }
                clearSession(closeProvider = true)
                showError(message)
            }
        })
        provider = currentProvider
        currentProvider.start()
        scope.launch {
            val result = audioCapture.start(scope) { frame ->
                scope.launch(Dispatchers.Main.immediate) {
                    val active = session
                    if (active?.id != current.id) return@launch
                    active.firstAudioSeen = true
                    publishReadyIfPossible(active)
                    provider?.appendAudio(frame)
                }
            }
            if (result.isFailure) {
                withContext(Dispatchers.Main.immediate) {
                    if (session?.id == current.id) {
                        clearSession(closeProvider = true)
                        showError("麦克风启动失败，请重试")
                    }
                }
            }
        }
    }

    fun stop() {
        val current = session ?: return
        if (state is SpokenVoiceState.Finishing) return
        setState(SpokenVoiceState.Finishing)
        scope.launch {
            audioCapture.stop()
            withContext(Dispatchers.Main.immediate) {
                if (session?.id == current.id) provider?.finish()
            }
        }
    }

    fun cancel() {
        VoicePermissionResult.clear(permissionCallback)
        resetJob?.cancel()
        session = null
        provider?.cancel()
        provider?.close()
        provider = null
        scope.launch { audioCapture.stop() }
        setState(SpokenVoiceState.Idle)
    }

    fun close() = cancel()

    private fun onSession(id: UUID, block: (Session) -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            val current = session ?: return@launch
            if (current.id == id) block(current)
        }
    }

    private fun publishReadyIfPossible(current: Session) {
        if (current.providerReady && current.firstAudioSeen && state is SpokenVoiceState.Starting) {
            setState(SpokenVoiceState.Recording(""))
        }
    }

    private fun clearSession(closeProvider: Boolean) {
        session = null
        if (closeProvider) provider?.close()
        provider = null
    }

    private fun showError(message: String) {
        setState(SpokenVoiceState.Error(message))
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(2_400)
            withContext(Dispatchers.Main.immediate) {
                if (state is SpokenVoiceState.Error) setState(SpokenVoiceState.Idle)
            }
        }
    }

    private fun setState(next: SpokenVoiceState) {
        state = next
        onState(next)
    }
}
