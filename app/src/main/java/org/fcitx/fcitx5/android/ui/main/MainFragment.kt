/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import androidx.fragment.app.activityViewModels
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.ui.main.settings.SpokenSettingsPage
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class MainFragment : SpokenSettingsPage() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onStart() { super.onStart(); viewModel.enableAboutButton() }
    override fun onStop() { viewModel.disableAboutButton(); super.onStop() }

    override fun buildPage() {
        val voice = AppPrefs.getInstance().voice
        heading("spoken", getString(R.string.spoken_settings_tagline))
        card().apply {
            row(getString(R.string.voice_and_ai), "${voice.asrProvider.getValue()} · ${voice.defaultScene.getValue().label}", R.drawable.ic_baseline_keyboard_voice_24) {
                navigateWithAnim(SettingsRoute.VoiceAi)
            }
            row(getString(R.string.spoken_keyboard_appearance), getString(R.string.spoken_keyboard_appearance_summary), R.drawable.ic_baseline_palette_24) {
                navigateWithAnim(SettingsRoute.KeyboardAppearance)
            }
            row(getString(R.string.spoken_input_habits), getString(R.string.spoken_input_habits_summary), R.drawable.ic_baseline_tune_24) {
                navigateWithAnim(SettingsRoute.InputOptions())
            }
        }
        card().apply {
            row(getString(R.string.spoken_privacy_advanced), getString(R.string.spoken_privacy_summary), R.drawable.ic_baseline_more_horiz_24) {
                navigateWithAnim(SettingsRoute.AdvancedHub)
            }
        }
        inputPlayground()
    }
}
