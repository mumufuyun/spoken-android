/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp

/** Fixed-height controls; the host editor owns the transcript whenever composing is supported. */
class SpokenVoiceDockView(
    context: Context,
    private val theme: Theme,
    onCancel: () -> Unit,
    onOptions: (View) -> Unit,
    onSettings: () -> Unit,
    onHideKeyboard: () -> Unit
) : LinearLayout(context) {
    private val status = TextView(context).apply {
        textSize = 13f
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), 0, dp(4), 0)
    }
    private val cancel = TextView(context).apply {
        text = "取消"
        textSize = 13f
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER
        isFocusable = true
        setOnClickListener { onCancel() }
    }
    private val scene = TextView(context).apply {
        textSize = 12f
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = true
        contentDescription = "语音场景与输出语言"
        setOnClickListener { onOptions(this) }
    }
    private val settings = ToolButton(context, R.drawable.ic_baseline_settings_24, theme).apply {
        contentDescription = context.getString(R.string.settings)
        isFocusable = true
        setOnClickListener { onSettings() }
    }
    private val hideKeyboard = ToolButton(
        context, R.drawable.ic_baseline_keyboard_arrow_down_24, theme
    ).apply {
        contentDescription = context.getString(R.string.hide_keyboard)
        isFocusable = true
        setOnClickListener { onHideKeyboard() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        // One surface also covers the transparent action buttons and their bottom edge.
        setBackgroundColor(theme.backgroundColor)
        addView(scene, LayoutParams(dp(80), -1))
        addView(status, LayoutParams(0, -1, 1f))
        addView(cancel, LayoutParams(dp(48), -1))
        addView(settings, LayoutParams(dp(48), -1))
        addView(hideKeyboard, LayoutParams(dp(48), -1))
        render(SpokenVoiceState.Idle)
    }

    fun render(state: SpokenVoiceState, fallback: String = "") {
        status.text = fallback.ifBlank {
            when (state) {
                SpokenVoiceState.Idle -> ""
                is SpokenVoiceState.Starting -> "正在连接，语音已暂存"
                is SpokenVoiceState.Recording -> "正在听…"
                SpokenVoiceState.Finishing -> "正在完成识别…"
                SpokenVoiceState.AiProcessing -> "AI 正在整理…"
                is SpokenVoiceState.Error -> state.message
                is SpokenVoiceState.Notice -> state.message
            }
        }
        status.contentDescription = status.text
        cancel.visibility = if (state.isActive) VISIBLE else GONE
        scene.text = AppPrefs.getInstance().voice.defaultScene.getValue().label + "⌄"
    }
}
