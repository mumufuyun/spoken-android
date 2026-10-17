/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Desktop 2.4.9 built-in mode policy; the IME keeps its own bounded call deadline. */
internal data class AiRequestPolicy(val parameters: JsonObject, val streaming: Boolean) {
    companion object {
        fun forInput(baseUrl: String, modelName: String, scene: WorkScene, text: String): AiRequestPolicy {
            val host = baseUrl.trim().toHttpUrlOrNull()?.host.orEmpty()
            val model = modelName.trim().lowercase(Locale.ROOT)
            val qwen = host == "dashscope.aliyuncs.com" || host == "dashscope-intl.aliyuncs.com" ||
                host.endsWith(".maas.aliyuncs.com")
            val minimax = host in setOf("api.minimax.cn", "api.minimax.chat", "api.minimaxi.com", "api.minimax.io")
            val qwenFlash = qwen && model == "qwen3.8-flash"
            val length = text.codePointCount(0, text.length)
            val meetingThinking = qwenFlash && scene == WorkScene.MINUTES && length >= 800
            val alwaysThinking = minimax && model.startsWith("minimax-m2")
            val outputTokens = when {
                meetingThinking -> 32_768
                alwaysThinking -> (length.toLong() * 4 + 1_024).coerceIn(2_048, 16_384).toInt()
                else -> (length.toLong() * 2 + 128).coerceIn(256, 16_384).toInt()
            }
            val parameters = buildJsonObject {
                put(if (minimax && model == "minimax-m3") "max_completion_tokens" else "max_tokens", outputTokens)
                if (qwen) put("temperature", 0.0)
                if (minimax) put("reasoning_split", true)
                when {
                    qwenFlash || (qwen && model.startsWith("deepseek-v4-")) ->
                        put("enable_thinking", meetingThinking)
                    minimax && model == "minimax-m3" ->
                        put("thinking", buildJsonObject { put("type", "disabled") })
                    (host == "api.deepseek.com" && model.startsWith("deepseek-v4-")) ||
                        (host == "open.bigmodel.cn" && (model.startsWith("glm-4.7") || model.startsWith("glm-5"))) ||
                        (host == "api.moonshot.cn" && model in setOf("kimi-k2.5", "kimi-k2.6")) ->
                        put("thinking", buildJsonObject { put("type", "disabled") })
                    // Unrecognized gateways/models retain their provider defaults. A model
                    // name alone does not establish which nonstandard fields an API accepts.
                }
            }
            return AiRequestPolicy(parameters, streaming = meetingThinking)
        }
    }
}
