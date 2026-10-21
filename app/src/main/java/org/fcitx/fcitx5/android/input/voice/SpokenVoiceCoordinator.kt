/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
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
    private val previewIfCurrent: (Long, String) -> Unit,
    private val discardPreview: () -> Unit,
    private val commitIfCurrent: (Long, String) -> Boolean,
    private val onModeCompleted: (WorkScene) -> Unit,
    private val onState: (SpokenVoiceState) -> Unit
) {
    private data class Session(
        val id: UUID,
        val editorGeneration: Long,
        val config: SpokenVoiceConfig,
        val aiProcessor: AiProcessor?,
        val audio: VoiceAudioSession,
        var scene: WorkScene = config.scene,
        var providerReady: Boolean = false,
        var firstAudioSeen: Boolean = false,
        var latestPartial: String = ""
    )

    private var session: Session? = null
    private var provider: SpokenAsrProvider? = null
    private var processing: VoiceProcessingSession? = null
    private var resetJob: Job? = null
    private val recovery = VoiceInputRecovery(SystemClock::elapsedRealtime)
    private var recoveryExpiryJob: Job? = null
    private var state: SpokenVoiceState = SpokenVoiceState.Idle
    private val permissionCallback: (Boolean) -> Unit = { granted ->
        scope.launch(Dispatchers.Main.immediate) {
            if (granted) start() else showError("需要麦克风权限才能使用语音输入")
        }
    }

    val isActive: Boolean
        get() = state.isActive

    fun selectScene(scene: WorkScene) {
        session?.scene = scene
        processing?.selectScene(scene)
    }

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
                it.latestPartial = text
                previewIfCurrent(it.editorGeneration, text)
                if (session?.id != it.id) return@onSession
                if (state !is SpokenVoiceState.Finishing) setState(SpokenVoiceState.Recording(text))
            }

            override fun onFinal(transcript: SpokenTranscript) = onSession(sessionId) {
                onTranscriptFinal(it, transcript)
            }

            override fun onError(message: String) = onSession(sessionId) {
                if (it.latestPartial.isNotBlank()) {
                    onTranscriptFinal(it, SpokenTranscript(it.latestPartial, isComplete = false))
                } else {
                    clearSession(closeProvider = true)
                    showError(message)
                }
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
            aiProcessor = createAiProcessor(config),
            audio = VoiceAudioSession(scope, AndroidVoiceAudioCapture())
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
        current.audio.start(
            onFrame = { frame ->
                scope.launch(Dispatchers.Main.immediate) {
                    val active = session
                    if (active?.id != current.id) return@launch
                    active.firstAudioSeen = true
                    publishReadyIfPossible(active)
                    provider?.appendAudio(frame)
                }
            },
            onFailure = {
                if (session?.id == current.id) {
                    clearSession(closeProvider = true)
                    showError("麦克风启动失败，请重试")
                }
            }
        )
    }

    fun stop() {
        val current = session ?: return
        if (state is SpokenVoiceState.Finishing) return
        setState(SpokenVoiceState.Finishing)
        val stopping = current.audio.stop()
        scope.launch {
            stopping.join()
            withContext(Dispatchers.Main.immediate) {
                if (session?.id == current.id) provider?.finish()
            }
        }
    }

    fun cancel(clearRecovery: Boolean = true) {
        VoicePermissionResult.clear(permissionCallback)
        resetJob?.cancel()
        processing?.cancel()
        processing = null
        session?.audio?.stop()
        session = null
        provider?.cancel()
        provider?.close()
        provider = null
        if (clearRecovery) {
            recoveryExpiryJob?.cancel()
            recovery.clear()
        }
        discardPreview()
        setState(SpokenVoiceState.Idle)
    }

    fun close() = cancel()

    /** The action freezes the entry identity; an old popup cannot insert a newer recording. */
    fun recoveryAction(): (() -> Unit)? {
        if (!editorAllowsVoice()) return null
        val entry = recovery.current(editorGeneration()) ?: return null
        return action@{
            if (!editorAllowsVoice() || recovery.current(editorGeneration())?.id != entry.id) return@action
            cancel(clearRecovery = false)
            prepareEditor()
            deliver(entry.editorGeneration, entry.transcript.text, if (entry.transcript.isComplete) {
                "已插入上次识别原文"
            } else "已插入上次原文，识别可能不完整")
        }
    }

    private fun createAiProcessor(config: SpokenVoiceConfig): AiProcessor? {
        if (!config.aiEnabled) return null
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

    private fun onTranscriptFinal(current: Session, transcript: SpokenTranscript) {
        val generation = current.editorGeneration
        if (transcript.text.isBlank()) {
            clearSession(closeProvider = true)
            showError("未检测到语音")
            return
        }
        previewIfCurrent(generation, transcript.text)
        if (session?.id != current.id || generation != editorGeneration()) return
        clearSession(closeProvider = true)
        recoveryExpiryJob?.cancel()
        recovery.capture(generation, transcript)?.let { entry ->
            recoveryExpiryJob = scope.launch {
                delay(VoiceInputRecovery.RETENTION_MS)
                if (recovery.current(generation)?.id == entry.id) recovery.clear()
            }
        }
        if (!transcript.isComplete) {
            deliver(generation, transcript.text, "识别未完整结束，已保留已识别文字，未进行 AI 整理")
            return
        }
        val next = VoiceProcessingSession(
            scope, transcript.text, current.scene, current.config.outputLanguage,
            current.config.aiEnabled, current.aiProcessor
        ) { text, notice, scene ->
            processing = null
            deliver(generation, text, notice, scene)
        }
        processing = next
        setState(SpokenVoiceState.AiProcessing)
        next.start()
    }

    private fun skipAiProcessing() = processing?.useOriginal()

    private fun onSession(id: UUID, block: (Session) -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            val current = session ?: return@launch
            if (current.id == id && current.editorGeneration == editorGeneration()) block(current)
        }
    }

    private fun publishReadyIfPossible(current: Session) {
        if (current.providerReady && current.firstAudioSeen && state is SpokenVoiceState.Starting) {
            setState(SpokenVoiceState.Recording(""))
        }
    }

    private fun clearSession(closeProvider: Boolean) {
        session?.audio?.stop()
        session = null
        if (closeProvider) provider?.close()
        provider = null
    }

    private fun showError(message: String) {
        discardPreview()
        setState(SpokenVoiceState.Error(message))
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(2_400)
            withContext(Dispatchers.Main.immediate) {
                if (state is SpokenVoiceState.Error) setState(SpokenVoiceState.Idle)
            }
        }
    }

    private fun deliver(
        generation: Long, text: String, notice: String? = null, completedScene: WorkScene? = null
    ) {
        if (generation != editorGeneration() || !editorAllowsVoice()) {
            cancel()
            return
        }
        val accepted = commitIfCurrent(generation, text)
        if (accepted && text.isNotBlank() && completedScene != null) onModeCompleted(completedScene)
        resetJob?.cancel()
        if (notice == null) {
            setState(SpokenVoiceState.Idle)
        } else {
            val next = SpokenVoiceState.Notice(notice)
            setState(next)
            resetJob = scope.launch {
                delay(4_000)
                if (state === next) setState(SpokenVoiceState.Idle)
            }
        }
    }

    private fun setState(next: SpokenVoiceState) {
        state = next
        onState(next)
    }
}
