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
    private val voiceConfig: () -> SpokenVoiceConfig,
    private val editorGeneration: () -> Long,
    private val editorAllowsVoice: () -> Boolean,
    private val prepareEditor: () -> Unit,
    private val commitIfCurrent: (Long, String) -> Unit,
    private val onState: (SpokenVoiceState) -> Unit
) {
    private data class Session(
        val id: UUID,
        val editorGeneration: Long,
        val config: SpokenVoiceConfig,
        val aiProcessor: AiProcessor?,
        var providerReady: Boolean = false,
        var firstAudioSeen: Boolean = false
    )

    private val audioCapture = AndroidVoiceAudioCapture()
    private var session: Session? = null
    private var provider: SpokenAsrProvider? = null
    private var aiJob: Job? = null
    // Raw transcript fallback while AiProcessing; lets a voice-key tap skip the AI round trip.
    private var aiFallback: Pair<Long, String>? = null
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
        when (voiceKeyTapAction(state)) {
            VoiceKeyTapAction.START -> start()
            VoiceKeyTapAction.STOP -> stop()
            VoiceKeyTapAction.SKIP_AI -> skipAiProcessing()
        }
    }

    fun start() {
        if (state.isActive) return
        if (!editorAllowsVoice()) {
            showError("当前输入框不允许语音输入")
            return
        }
        val config = voiceConfig()
        if (!AsrProviderFactory.isConfigured(config, keyStorage)) {
            showError("请先在 Spoken 设置中完成语音服务配置")
            return
        }
        val sessionId = UUID.randomUUID()
        val listener = object : SpokenAsrProvider.Listener {
            override fun onReady() = onSession(sessionId) {
                it.providerReady = true
                publishReadyIfPossible(it)
            }

            override fun onPartial(text: String) = onSession(sessionId) {
                setState(SpokenVoiceState.Recording(text))
            }

            override fun onFinal(text: String) = onSession(sessionId) {
                onTranscriptFinal(it, text)
            }

            override fun onError(message: String) = onSession(sessionId) {
                scope.launch { audioCapture.stop() }
                clearSession(closeProvider = true)
                showError(message)
            }
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
        val current = Session(
            id = sessionId,
            editorGeneration = editorGeneration(),
            config = config,
            aiProcessor = createAiProcessor(config)
        )
        session = current
        setState(SpokenVoiceState.Starting("正在连接云端，语音已暂存…"))
        val currentProvider = AsrProviderFactory.create(config, keyStorage, listener)
        if (currentProvider == null) {
            clearSession(closeProvider = false)
            showError("请先在 Spoken 设置中完成语音服务配置")
            return
        }
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
        aiJob?.cancel()
        aiJob = null
        aiFallback = null
        session = null
        provider?.cancel()
        provider?.close()
        provider = null
        scope.launch { audioCapture.stop() }
        setState(SpokenVoiceState.Idle)
    }

    fun close() = cancel()

    private fun createAiProcessor(config: SpokenVoiceConfig): AiProcessor? {
        if (!config.aiEnabled || config.workMode == WorkMode.DIRECT) return null
        val apiKey = keyStorage.load(VoiceKeyPurpose.AI_KEY)?.takeIf(String::isNotBlank) ?: return null
        return AiProcessor(
            enabled = true,
            client = AiClient(
                AiConfig(
                    baseUrl = config.aiBaseUrl,
                    apiKey = apiKey,
                    model = config.aiModel,
                    timeoutSec = config.aiTimeoutSec
                )
            )
        )
    }

    private fun onTranscriptFinal(current: Session, text: String) {
        val generation = current.editorGeneration
        clearSession(closeProvider = true)
        val processed = SpokenSpeechPostProcessor.process(text)
        if (processed.isEmpty()) {
            showError("未检测到语音")
            return
        }
        val processor = current.aiProcessor
        if (processor == null) {
            commitIfCurrent(generation, processed)
            setState(SpokenVoiceState.Idle)
            return
        }
        aiFallback = generation to processed
        setState(SpokenVoiceState.AiProcessing)
        aiJob = scope.launch {
            val result = processor.process(processed, current.config.workMode)
            withContext(Dispatchers.Main.immediate) {
                aiJob = null
                if (state is SpokenVoiceState.AiProcessing) {
                    aiFallback = null
                    commitIfCurrent(generation, result)
                    setState(SpokenVoiceState.Idle)
                }
            }
        }
    }

    private fun skipAiProcessing() {
        val (generation, text) = aiFallback ?: return
        aiFallback = null
        aiJob?.cancel()
        aiJob = null
        commitIfCurrent(generation, text)
        setState(SpokenVoiceState.Idle)
    }

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
