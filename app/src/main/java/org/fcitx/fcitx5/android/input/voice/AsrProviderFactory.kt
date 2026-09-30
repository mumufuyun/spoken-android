/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.fcitx.fcitx5.android.data.prefs.AppPrefs

enum class SpokenAsrProviderKind { QWEN, XUNFEI, CUSTOM }

/**
 * Immutable snapshot of voice-related settings captured when a session starts, so
 * preference changes mid-session do not affect the ongoing session.
 */
data class SpokenVoiceConfig(
    val asrProvider: SpokenAsrProviderKind,
    val xunfeiAppId: String,
    val customAsrUrl: String,
    val customAsrModel: String,
    val aiEnabled: Boolean,
    val aiBaseUrl: String,
    val aiModel: String,
    val aiTimeoutSec: Int,
    val scene: WorkScene,
    val outputLanguage: OutputLanguage
) {
    companion object {
        fun fromPrefs(prefs: AppPrefs): SpokenVoiceConfig {
            val voice = prefs.voice
            return SpokenVoiceConfig(
                asrProvider = voice.asrProvider.getValue(),
                xunfeiAppId = voice.xunfeiAppId.getValue(),
                customAsrUrl = voice.customAsrUrl.getValue(),
                customAsrModel = voice.customAsrModel.getValue(),
                aiEnabled = voice.aiEnabled.getValue(),
                aiBaseUrl = voice.aiBaseUrl.getValue(),
                aiModel = voice.aiModel.getValue(),
                aiTimeoutSec = voice.aiTimeoutSec.getValue(),
                scene = voice.defaultScene.getValue(),
                outputLanguage = voice.outputLanguage.getValue()
            )
        }
    }
}

object AsrProviderFactory {
    /**
     * Whether the selected engine's required credentials and endpoint are present.
     */
    fun isConfigured(config: SpokenVoiceConfig, keyStorage: SecureVoiceKeyStorage): Boolean =
        when (config.asrProvider) {
            SpokenAsrProviderKind.QWEN ->
                !keyStorage.load(VoiceKeyPurpose.QWEN).isNullOrBlank()

            SpokenAsrProviderKind.XUNFEI ->
                config.xunfeiAppId.isNotBlank() &&
                    !keyStorage.load(VoiceKeyPurpose.XUNFEI_KEY).isNullOrBlank() &&
                    !keyStorage.load(VoiceKeyPurpose.XUNFEI_SECRET).isNullOrBlank()

            SpokenAsrProviderKind.CUSTOM -> config.customAsrUrl.isNotBlank()
        }

    /**
     * Returns null when the selected engine's required credentials or endpoint are missing.
     */
    fun create(
        config: SpokenVoiceConfig,
        keyStorage: SecureVoiceKeyStorage,
        listener: SpokenAsrProvider.Listener
    ): SpokenAsrProvider? = when (config.asrProvider) {
        SpokenAsrProviderKind.QWEN ->
            keyStorage.load(VoiceKeyPurpose.QWEN)?.takeIf(String::isNotBlank)?.let {
                QwenRealtimeAsrProvider(it, listener)
            }

        SpokenAsrProviderKind.XUNFEI -> {
            val apiKey = keyStorage.load(VoiceKeyPurpose.XUNFEI_KEY)?.takeIf(String::isNotBlank)
            val apiSecret = keyStorage.load(VoiceKeyPurpose.XUNFEI_SECRET)?.takeIf(String::isNotBlank)
            if (config.xunfeiAppId.isBlank() || apiKey == null || apiSecret == null) null
            else XunfeiAsrProvider(config.xunfeiAppId, apiKey, apiSecret, listener)
        }

        SpokenAsrProviderKind.CUSTOM ->
            config.customAsrUrl.takeIf(String::isNotBlank)?.let {
                CustomAsrProvider(
                    endpointUrl = it,
                    apiKey = keyStorage.load(VoiceKeyPurpose.CUSTOM_KEY).orEmpty(),
                    model = config.customAsrModel,
                    listener = listener
                )
            }
    }
}
