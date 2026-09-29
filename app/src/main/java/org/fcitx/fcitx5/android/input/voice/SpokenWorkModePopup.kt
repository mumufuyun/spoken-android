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
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.radiusDrawable
import splitties.dimensions.dp

/**
 * Scene-mode picker shown on voice-key long press: a 3-column grid following the desktop
 * ModeGrid spec (16dp container corner, 10dp cells, 8dp gaps, accent selection).
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
        val modePref = AppPrefs.getInstance().voice.defaultMode
        val cells = mutableMapOf<WorkMode, LinearLayout>()

        val grid = GridLayout(ctx).apply {
            columnCount = 3
            val padding = 10.px()
            setPadding(padding, padding, padding, padding)
        }

        lateinit var popup: PopupWindow

        fun renderSelection() {
            cells.forEach { (mode, cell) ->
                val selected = mode == modePref.getValue()
                cell.background = radiusDrawable(
                    10.px().toFloat(),
                    if (selected) theme.accentKeyBackgroundColor else theme.altKeyBackgroundColor
                )
                val foreground =
                    if (selected) theme.accentKeyTextColor else theme.keyTextColor
                (cell.getChildAt(0) as ImageView).setColorFilter(foreground)
                (cell.getChildAt(1) as TextView).setTextColor(foreground)
                cell.getChildAt(2).visibility = if (selected) View.VISIBLE else View.INVISIBLE
            }
        }

        WorkMode.entries.forEach { mode ->
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = 40.px()
                val verticalPadding = 8.px()
                setPadding(4.px(), verticalPadding, 4.px(), verticalPadding)
                val icon = ImageView(ctx).apply {
                    setImageResource(iconFor(mode))
                    layoutParams = LinearLayout.LayoutParams(18.px(), 18.px())
                }
                val name = TextView(ctx).apply {
                    text = mode.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }
                val check = TextView(ctx).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    setTextColor(theme.accentKeyTextColor)
                }
                addView(icon)
                addView(name)
                addView(check)
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

        grid.background = GradientDrawable().apply {
            cornerRadius = 16.px().toFloat()
            setColor(theme.popupBackgroundColor)
            setStroke(1.px(), theme.dividerColor)
        }
        popup = PopupWindow(
            grid,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8.px().toFloat()
        }

        grid.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.width - grid.measuredWidth) / 2
        val y = -(anchor.height + grid.measuredHeight)
        popup.showAsDropDown(anchor, x, y, Gravity.START)
    }
}
