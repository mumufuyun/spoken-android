/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.voice.AsrProviderFactory
import org.fcitx.fcitx5.android.input.voice.SecureVoiceKeyStorage
import org.fcitx.fcitx5.android.input.voice.SpokenVoiceConfig
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.appContext

enum class SetupPage {
    Enable, Select, Microphone, VoiceConfig;

    fun getHintText(context: Context) = context.getString(
        when (this) {
            Enable -> R.string.enable_ime_hint
            Select -> R.string.select_ime_hint
            Microphone -> R.string.setup_microphone_hint
            VoiceConfig -> R.string.setup_voice_config_hint
        }, context.getString(R.string.app_name)
    ).let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }

    fun getButtonText(context: Context) = context.getText(
        when (this) {
            Enable -> R.string.enable_ime
            Select -> R.string.select_ime
            Microphone -> R.string.grant_permission
            VoiceConfig -> R.string.setup_voice_config
        }
    )

    fun getButtonAction(context: Context) {
        when (this) {
            Enable -> InputMethodUtil.startSettingsActivity(context)
            Select -> InputMethodUtil.showPicker()
            Microphone -> (context as? Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            }
            VoiceConfig -> AppUtil.launchMainToVoiceSettings(context)
        }
    }

    fun isDone() = when (this) {
        Enable -> InputMethodUtil.isEnabled()
        Select -> InputMethodUtil.isSelected()
        Microphone -> ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        VoiceConfig -> AsrProviderFactory.isConfigured(
            SpokenVoiceConfig.fromPrefs(AppPrefs.getInstance()),
            SecureVoiceKeyStorage(appContext)
        )
    }

    companion object {
        /**
         * Pages that force the wizard to pop up on cold start. The microphone and voice
         * configuration steps are optional enhancements: they render inside the wizard but
         * never gate it, so existing users are not intercepted after upgrading.
         */
        internal val blockingPages = listOf(Enable, Select)

        fun valueOf(value: Int) = entries[value]
        fun SetupPage.isLastPage() = this == entries.last()
        fun Int.isLastPage() = this == entries.size - 1
        fun hasUndonePage() = entries.any { !it.isDone() }
        fun hasUndoneBlockingPage() = blockingPages.any { !it.isDone() }
        fun firstUndonePage() = entries.firstOrNull { !it.isDone() }
    }
}
