/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.voice.AsrProviderFactory
import org.fcitx.fcitx5.android.input.voice.OutputLanguage
import org.fcitx.fcitx5.android.input.voice.SecureVoiceKeyStorage
import org.fcitx.fcitx5.android.input.voice.SpokenVoiceConfig
import org.fcitx.fcitx5.android.input.voice.VoiceKeyPurpose
import org.fcitx.fcitx5.android.input.voice.WorkScene
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import splitties.dimensions.dp

class KeyboardAppearanceFragment : SpokenSettingsPage() {
    override fun buildPage() {
        val prefs = AppPrefs.getInstance().keyboard
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val heightPreference = if (landscape) prefs.t9KeyboardHeightPercentLandscape else prefs.t9KeyboardHeightPercent
        val heightLabel = if (landscape) R.string.spoken_height_value_landscape else R.string.spoken_height_value
        heading(getString(R.string.spoken_keyboard_appearance), getString(R.string.spoken_height_hint))
        card().apply {
            setPadding(dp(16), dp(16), dp(16), dp(12))
            val value = label(getString(heightLabel, heightPreference.getValue()), 16f, true)
            addView(value)
            val choices = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(8))
            }
            val buttons = mutableMapOf<Int, TextView>()
            fun refresh(percent: Int) {
                value.text = getString(heightLabel, percent)
                buttons.forEach { (height, button) ->
                    button.background = rounded(if (height == percent) accent else surface, 12)
                    button.setTextColor(if (height == percent) android.graphics.Color.rgb(42, 23, 17) else ink)
                    button.isSelected = height == percent
                }
            }
            val slider = SeekBar(context).apply {
                max = 20
                progress = heightPreference.getValue().coerceIn(20, 40) - 20
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                contentDescription = getString(R.string.t9_keyboard_height)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) heightPreference.setValue(progress + 20)
                        refresh(progress + 20)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }
            val presets = if (landscape) listOf(32, 35, 38) else listOf(23, 28, 31)
            presets.zip(listOf(R.string.spoken_height_compact, R.string.spoken_height_comfortable, R.string.spoken_height_spacious)).forEach { (height, name) ->
                val button = label(getString(name), 14f, true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    isFocusable = true
                    setOnClickListener {
                        heightPreference.setValue(height)
                        slider.progress = height - 20
                        refresh(height)
                    }
                }
                buttons[height] = button
                choices.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            }
            addView(choices)
            addView(slider, LinearLayout.LayoutParams(-1, dp(48)))
            refresh(heightPreference.getValue())
        }
        card().apply {
            row(getString(R.string.theme), getString(R.string.spoken_keyboard_appearance_summary), R.drawable.ic_baseline_palette_24) {
                navigateWithAnim(SettingsRoute.Theme)
            }
            row(getString(R.string.keys_and_toolbar), getString(R.string.input_feedback_tuning), R.drawable.ic_baseline_keyboard_24) {
                navigateWithAnim(SettingsRoute.KeysAndToolbar)
            }
            row(getString(R.string.appearance_and_candidates), getString(R.string.candidates_window), R.drawable.ic_baseline_tune_24) {
                navigateWithAnim(SettingsRoute.AppearanceAndCandidates)
            }
        }
        inputPlayground()
    }
}

class VoiceSettingsFragment : SpokenSettingsPage() {
    override fun buildPage() {
        val prefs = AppPrefs.getInstance()
        val voice = prefs.voice
        val keys = SecureVoiceKeyStorage(requireContext())
        heading(getString(R.string.voice_and_ai), getString(R.string.spoken_voice_hint))
        val asrReady = AsrProviderFactory.isConfigured(SpokenVoiceConfig.fromPrefs(prefs), keys)
        card().apply {
            row(getString(R.string.voice_recognition), "${voice.asrProvider.getValue()} · ${getString(if (asrReady) R.string.spoken_connection_saved else R.string.spoken_connection_missing)}", R.drawable.ic_baseline_keyboard_voice_24) {
                navigateWithAnim(SettingsRoute.VoiceConnection(ai = false))
            }
        }
        card().apply {
            val toggle = SwitchCompat(context).apply {
                text = getString(R.string.ai_enabled)
                textSize = 16f
                setTextColor(ink)
                setPadding(dp(16), dp(12), dp(16), dp(8))
                minHeight = dp(56)
                isChecked = voice.aiEnabled.getValue()
                setOnCheckedChangeListener { _, enabled -> voice.aiEnabled.setValue(enabled) }
            }
            addView(toggle)
            row(getString(R.string.spoken_connection_ai), "${voice.aiModel.getValue()} · ${getString(if (keys.has(VoiceKeyPurpose.AI_KEY)) R.string.spoken_connection_saved else R.string.spoken_connection_missing)}", R.drawable.ic_baseline_tune_24) {
                navigateWithAnim(SettingsRoute.VoiceConnection(ai = true))
            }
        }
        section(getString(R.string.spoken_preferences))
        card().apply {
            row(getString(R.string.default_scene), voice.defaultScene.getValue().label, R.drawable.ic_baseline_tune_24) {
                val entries = WorkScene.entries
                AlertDialog.Builder(requireContext()).setTitle(R.string.default_scene)
                    .setSingleChoiceItems(entries.map { it.label }.toTypedArray(), entries.indexOf(voice.defaultScene.getValue())) { dialog, which ->
                        voice.defaultScene.setValue(entries[which]); dialog.dismiss()
                        content.removeAllViews(); buildPage()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            row(getString(R.string.voice_modes_order), getString(R.string.voice_modes_order_summary), R.drawable.ic_baseline_tune_24) {
                VoiceModeOrderDialog.show(requireContext())
            }
            row(getString(R.string.output_language), voice.outputLanguage.getValue().label, R.drawable.ic_baseline_language_24) {
                val entries = OutputLanguage.entries
                AlertDialog.Builder(requireContext()).setTitle(R.string.output_language)
                    .setSingleChoiceItems(entries.map { it.label }.toTypedArray(), entries.indexOf(voice.outputLanguage.getValue())) { dialog, which ->
                        voice.outputLanguage.setValue(entries[which]); dialog.dismiss()
                        content.removeAllViews(); buildPage()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }
    }
}
