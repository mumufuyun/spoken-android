/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp

/** Fixed-height controls. Scrolling browses scenes; only a tap changes the current/next scene. */
class SpokenVoiceDockView(
    context: Context,
    private val theme: Theme,
    initialOrder: List<WorkScene>,
    private val onSceneSelected: (WorkScene) -> Unit,
    onCancel: () -> Unit,
    onOptions: (View) -> Unit,
    onSettings: () -> Unit,
    onHideKeyboard: () -> Unit
) : LinearLayout(context) {
    private val prefs get() = AppPrefs.getInstance().voice
    private var active = false
    private var selected: WorkScene? = null
    private val chips = linkedMapOf<WorkScene, TextView>()
    private val modeRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val modes = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        addView(modeRow, LayoutParams(-2, -1))
    }
    private val status = TextView(context).apply {
        textSize = 13f
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4), 0, dp(4), 0)
    }
    private val cancel = TextView(context).apply {
        text = context.getString(android.R.string.cancel)
        textSize = 13f
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER
        isFocusable = true
        setOnClickListener { onCancel() }
    }
    private val language = TextView(context).apply {
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(theme.keyTextColor)
        isFocusable = true
        setOnClickListener { if (!active) onOptions(this) }
    }
    private val settings = ToolButton(context, R.drawable.ic_baseline_settings_24, theme).apply {
        contentDescription = context.getString(R.string.settings)
        isFocusable = true
        setOnClickListener { onSettings() }
    }
    private val dismissTouchGuard = KeyboardDismissTouchGuard(
        bottomInset = dp(10).toFloat(),
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    )
    private val hideKeyboard = ToolButton(
        context, R.drawable.ic_baseline_keyboard_arrow_down_24, theme
    ).apply {
        contentDescription = context.getString(R.string.hide_keyboard)
        isFocusable = true
        // Keep semantic/accessibility clicks available without the touch-only guard.
        setOnClickListener { onHideKeyboard() }
        setOnTouchListener { view, event ->
            if (!view.isEnabled || event.pointerCount != 1) {
                dismissTouchGuard.cancel()
            } else when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dismissTouchGuard.down(event.x, event.y, view.width.toFloat(),
                        view.height.toFloat(), event.eventTime)
                    if (dismissTouchGuard.isTracking) {
                        view.drawableHotspotChanged(event.x, event.y)
                        InputFeedbacks.hapticFeedback(view)
                        InputFeedbacks.soundEffect(InputFeedbacks.SoundEffect.Standard)
                    }
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    for (i in 0 until event.historySize) {
                        dismissTouchGuard.move(event.getHistoricalX(i), event.getHistoricalY(i))
                    }
                    dismissTouchGuard.move(event.x, event.y)
                    if (event.actionMasked == MotionEvent.ACTION_UP &&
                        dismissTouchGuard.up(event.x, event.y, event.eventTime)
                    ) view.performClick()
                }
                else -> dismissTouchGuard.cancel()
            }
            view.isPressed = dismissTouchGuard.isTracking
            // Consume rejected touches instead of passing them to the key gesture handler.
            true
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(theme.backgroundColor)
        addView(FrameLayout(context).apply {
            addView(modes, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(0, -1, 1f))
        addView(cancel, LayoutParams(dp(48), -1))
        addView(language, LayoutParams(dp(44), -1))
        addView(settings, LayoutParams(dp(44), -1))
        addView(hideKeyboard, LayoutParams(dp(44), -1))
        setModeOrder(initialOrder)
        render(SpokenVoiceState.Idle)
    }

    fun onKeyboardTouch(time: Long) {
        dismissTouchGuard.keyboardTouched(time)
        hideKeyboard.isPressed = false
    }

    override fun onDetachedFromWindow() {
        dismissTouchGuard.cancel()
        hideKeyboard.isPressed = false
        super.onDetachedFromWindow()
    }

    fun setModeOrder(order: List<WorkScene>) {
        modeRow.removeAllViews()
        chips.clear()
        order.forEach { scene ->
            val chip = TextView(context).apply {
                text = scene.chipLabel
                textSize = 13f
                InputUiFont.applyTo(this)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                minimumWidth = dp(48)
                maxLines = 1
                isFocusable = true
                contentDescription = scene.label + "：" + scene.description
                tooltipText = scene.description
                setOnClickListener {
                    onSceneSelected(scene)
                    renderSelection()
                }
            }
            chips[scene] = chip
            modeRow.addView(chip, LayoutParams(-2, -1))
        }
        renderSelection()
        revealSelection()
    }

    private fun renderSelection() {
        val current = prefs.defaultScene.getValue()
        chips.forEach { (scene, chip) ->
            val checked = scene == current
            chip.isSelected = checked
            val description = if (scene == WorkScene.DIRECT &&
                prefs.outputLanguage.getValue() != OutputLanguage.ORIGINAL
            ) context.getString(R.string.voice_picker_translate) else scene.description
            chip.contentDescription = scene.label + "：" + description
            chip.tooltipText = description
            chip.setTextColor(if (checked) theme.accentKeyTextColor else theme.keyTextColor)
            chip.background = RippleDrawable(
                ColorStateList.valueOf(theme.keyPressHighlightColor),
                InsetDrawable(GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (checked) theme.accentKeyBackgroundColor else theme.backgroundColor)
                }, dp(2), dp(7), dp(2), dp(7)), null
            )
        }
        if (selected != current) {
            selected = current
            revealSelection()
        }
    }

    private fun revealSelection() {
        modes.post {
            val chip = chips[prefs.defaultScene.getValue()] ?: return@post
            if (chip.left < modes.scrollX) modes.scrollTo(chip.left, 0)
            else if (chip.right > modes.scrollX + modes.width) modes.scrollTo(chip.right - modes.width, 0)
        }
    }

    fun render(state: SpokenVoiceState, fallback: String = "") {
        active = state.isActive
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
        val showStatus = status.text.isNotEmpty() && (!active || fallback.isNotBlank())
        val returning = modes.visibility != VISIBLE && !showStatus
        status.visibility = if (showStatus) VISIBLE else GONE
        modes.visibility = if (showStatus && !active) INVISIBLE else VISIBLE
        // Unsupported host editors retain a compact preview below the live scene strip.
        modes.layoutParams = FrameLayout.LayoutParams(-1, if (showStatus && active) dp(28) else -1)
        status.layoutParams = FrameLayout.LayoutParams(-1, if (active) dp(22) else -1,
            if (active) Gravity.BOTTOM else Gravity.CENTER)
        status.maxLines = if (active) 1 else 2
        language.visibility = if (active) GONE else VISIBLE
        settings.visibility = if (active) GONE else VISIBLE
        cancel.visibility = if (active) VISIBLE else GONE
        language.isEnabled = !active
        settings.isEnabled = !active
        language.alpha = if (active) 0.4f else 1f
        settings.alpha = language.alpha
        language.text = prefs.outputLanguage.getValue().chipLabel + "⌄"
        language.contentDescription = context.getString(R.string.output_language) + "：" + prefs.outputLanguage.getValue().label
        renderSelection()
        if (returning) revealSelection()
    }
}
