/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import splitties.dimensions.dp

/**
 * Voice session status area for the candidate dock: a semantic status dot, the status or live
 * transcript text, and the 24-bar waveform while a session is capturing or processing.
 */
class SpokenVoiceStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val theme: Theme = ThemeManager.activeTheme,
    labelStyle: TextView.() -> Unit = {}
) : LinearLayout(context, attrs) {

    private val dotBackground = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private val dot = View(context).apply {
        background = dotBackground
    }

    private val label = TextView(context).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        labelStyle()
    }

    private val waveform = SpokenWaveformView(context).apply {
        visibility = View.GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dotSize = dp(8)
        addView(dot, LayoutParams(dotSize, dotSize).apply {
            marginEnd = dp(8)
        })
        addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val waveWidth = SpokenWaveformView.BAR_COUNT * dp(4) +
            (SpokenWaveformView.BAR_COUNT - 1) * dp(4)
        addView(waveform, LayoutParams(waveWidth, LayoutParams.MATCH_PARENT).apply {
            marginStart = dp(8)
        })
    }

    fun render(state: SpokenVoiceState, text: String) {
        dotBackground.setColor(
            SpokenVoiceVisuals.statusDotColor(state, theme.accentKeyBackgroundColor)
        )
        label.text = text
        label.setTextColor(
            if (state is SpokenVoiceState.Error) SpokenVoiceVisuals.COLOR_ERROR
            else theme.candidateTextColor
        )
        val gradient = SpokenVoiceVisuals.waveformGradient(state)
        if (gradient != null) {
            waveform.visibility = View.VISIBLE
            waveform.start(gradient)
        } else {
            waveform.stop()
            waveform.visibility = View.GONE
        }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != View.VISIBLE) waveform.stop()
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        waveform.stop()
        super.onDetachedFromWindow()
    }
}
