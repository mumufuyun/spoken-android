/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.FlatPopTints
import splitties.dimensions.dp

/**
 * Scene-mode picker shown on voice-key long press, following the Flat Pop sheet (mockup 15):
 * 16dp sheet corner with a hairline border, a dim letter-spaced title, and a 3-column grid of
 * 12dp-corner tiles carrying per-mode macaron tints. The selected tile is solid accent with a
 * green check badge.
 */
object SpokenWorkModePopup {

    @DrawableRes
    private fun iconFor(mode: WorkMode): Int = when (mode) {
        WorkMode.DIRECT -> R.drawable.ic_baseline_format_quote_24
        WorkMode.POLISH -> R.drawable.ic_baseline_edit_24
        WorkMode.PROMPT -> R.drawable.ic_baseline_auto_awesome_24
        WorkMode.TRANSLATE -> R.drawable.ic_baseline_language_24
        WorkMode.MINUTES -> R.drawable.ic_baseline_emoji_people_24
        WorkMode.STRUCTURE -> R.drawable.ic_baseline_list_alt_24
    }

    fun show(anchor: View, theme: Theme) {
        val ctx = anchor.context
        fun Int.px() = ctx.dp(this)
        val dark = theme.isDark
        val modePref = AppPrefs.getInstance().voice.defaultMode
        val cells = mutableMapOf<WorkMode, FrameLayout>()

        val grid = GridLayout(ctx).apply {
            columnCount = 3
        }
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.px(), 10.px(), 12.px(), 12.px())
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.work_mode_picker_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(theme.candidateCommentColor)
                letterSpacing = 0.17f
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.px() })
            addView(grid)
        }

        lateinit var popup: PopupWindow

        fun tileBackground(mode: WorkMode): GradientDrawable {
            val selected = mode == modePref.getValue()
            val tint = FlatPopTints.modeTint(mode, dark)
            return GradientDrawable().apply {
                cornerRadius = 12.px().toFloat()
                when {
                    selected -> setColor(theme.accentKeyBackgroundColor)
                    tint != null -> setColor(tint)
                    else -> {
                        setColor(theme.keyBackgroundColor)
                        setStroke(1.px(), theme.dividerColor)
                    }
                }
            }
        }

        fun renderSelection() {
            cells.forEach { (mode, cell) ->
                val selected = mode == modePref.getValue()
                cell.background = tileBackground(mode)
                val content = cell.getChildAt(0) as LinearLayout
                val foreground =
                    if (selected) theme.accentKeyTextColor else theme.keyTextColor
                (content.getChildAt(0) as ImageView).setColorFilter(foreground)
                (content.getChildAt(1) as TextView).setTextColor(foreground)
                cell.getChildAt(1).visibility = if (selected) View.VISIBLE else View.GONE
            }
        }

        WorkMode.entries.forEach { mode ->
            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val icon = ImageView(ctx).apply {
                    setImageResource(iconFor(mode))
                    layoutParams = LinearLayout.LayoutParams(20.px(), 20.px())
                }
                val name = TextView(ctx).apply {
                    text = mode.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                addView(icon)
                addView(name, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.px() })
            }
            val check = TextView(ctx).apply {
                text = "✓"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(SpokenVoiceVisuals.readyColor(dark))
                }
                visibility = View.GONE
            }
            val cell = FrameLayout(ctx).apply {
                minimumHeight = 36.px()
                addView(content)
                addView(check, FrameLayout.LayoutParams(18.px(), 18.px()).apply {
                    gravity = Gravity.TOP or Gravity.END
                })
                setOnClickListener {
                    modePref.setValue(mode)
                    renderSelection()
                    popup.dismiss()
                }
            }
            cells[mode] = cell
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 88.px()
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                val margin = 4.px()
                setMargins(margin, margin, margin, margin)
            })
        }
        renderSelection()

        sheet.background = GradientDrawable().apply {
            cornerRadius = 16.px().toFloat()
            setColor(theme.popupBackgroundColor)
            setStroke(1.px(), theme.dividerColor)
        }
        popup = PopupWindow(
            sheet,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8.px().toFloat()
        }

        sheet.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.width - sheet.measuredWidth) / 2
        val y = -(anchor.height + sheet.measuredHeight)
        popup.showAsDropDown(anchor, x, y, Gravity.START)
    }
}
