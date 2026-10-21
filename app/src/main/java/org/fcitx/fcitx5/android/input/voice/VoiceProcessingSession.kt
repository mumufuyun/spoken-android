/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One original transcript, with replaceable AI requests confined to its editor session. */
internal class VoiceProcessingSession(
    private val scope: CoroutineScope,
    private val original: String,
    initialScene: WorkScene,
    private val language: OutputLanguage,
    private val aiEnabled: Boolean,
    private val processor: AiProcessor?,
    private val deliver: (String, String?, WorkScene?) -> Unit
) {
    private var scene = initialScene
    private var active = true
    private var request: Any? = null
    private var job: Job? = null

    fun selectScene(next: WorkScene) {
        if (!active || next == scene) return
        scene = next
        start()
    }

    fun start() {
        if (!active) return
        request = null
        job?.cancel()
        job = null
        val selected = scene
        if (selected == WorkScene.DIRECT && language == OutputLanguage.ORIGINAL) {
            finish(original, null, selected)
            return
        }
        if (processor == null) {
            finish(original, if (aiEnabled) "AI 尚未配置，已输入原文" else "AI 未启用，已输入原文", null)
            return
        }
        val token = Any()
        request = token
        job = scope.launch(start = CoroutineStart.LAZY) {
            // Always restart from ASR, never from the output of a previous scene.
            val result = processor.process(SpokenSpeechPostProcessor.process(original), selected, language)
            if (!active || request !== token) return@launch
            job = null
            val notice = when (result.fallback) {
                null -> null
                AiFallbackReason.UNSAFE_OUTPUT -> "AI 返回内容异常，已输入原文"
                else -> "AI 整理未完成，已输入原文"
            }
            finish(if (result.fallback == null) result.text else original, notice,
                selected.takeIf { result.fallback == null })
        }
        job?.start()
    }

    fun useOriginal() {
        if (active) finish(original, "已跳过 AI，输入原文", null)
    }

    fun cancel() {
        active = false
        request = null
        job?.cancel()
        job = null
    }

    private fun finish(text: String, notice: String?, completedScene: WorkScene?) {
        cancel()
        deliver(text, notice, completedScene)
    }
}
