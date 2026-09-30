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
 * Scene × language picker shown on voice-key long press: a Flat Pop sheet with a row of output
 * language chips on top and a 3-column scene grid below (DIRECT first). Selections write the
 * voice prefs and take effect from the next voice session.
 */
object SpokenWorkModePopup {

    private var current: PopupWindow? = null

    /** Dismiss on keyboard detach so the popup never outlives its window token. */
    fun dismissCurrent() {
        current?.dismiss()
        current = null
    }

    @DrawableRes
    private fun iconFor(scene: WorkScene): Int = when (scene) {
        WorkScene.DIRECT -> R.drawable.ic_baseline_format_quote_24
        WorkScene.CHAT -> R.drawable.ic_baseline_send_24
        WorkScene.WORK -> R.drawable.ic_baseline_emoji_people_24
        WorkScene.FORMAL -> R.drawable.ic_baseline_library_books_24
        WorkScene.MINUTES -> R.drawable.ic_baseline_list_alt_24
        WorkScene.SHARE -> R.drawable.ic_baseline_share_24
        WorkScene.PROMPT -> R.drawable.ic_baseline_auto_awesome_24
    }

    fun show(anchor: View, theme: Theme) {
        val ctx = anchor.context
        fun Int.px() = ctx.dp(this)
        val dark = theme.isDark
        val voicePrefs = AppPrefs.getInstance().voice
        val scenePref = voicePrefs.defaultScene
        val languagePref = voicePrefs.outputLanguage
        val cells = mutableMapOf<WorkScene, FrameLayout>()
        val chips = mutableMapOf<OutputLanguage, TextView>()

        val grid = GridLayout(ctx).apply {
            columnCount = 3
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.px(), 10.px(), 12.px(), 12.px())
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.scene_language_picker_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(theme.candidateCommentColor)
                letterSpacing = 0.17f
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.px() })
            addView(chipRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.px() })
            addView(grid)
        }

        lateinit var popup: PopupWindow

        fun pill(selected: Boolean): GradientDrawable = GradientDrawable().apply {
            cornerRadius = 999.px().toFloat()
            setColor(
                if (selected) theme.accentKeyBackgroundColor else theme.altKeyBackgroundColor
            )
        }

        fun renderSelection() {
            cells.forEach { (scene, cell) ->
                val selected = scene == scenePref.getValue()
                val tint = FlatPopTints.sceneTint(scene, dark)
                cell.background = GradientDrawable().apply {
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
                val content = cell.getChildAt(0) as LinearLayout
                val foreground =
                    if (selected) theme.accentKeyTextColor else theme.keyTextColor
                (content.getChildAt(0) as ImageView).setColorFilter(foreground)
                (content.getChildAt(1) as TextView).setTextColor(foreground)
                cell.getChildAt(1).visibility = if (selected) View.VISIBLE else View.GONE
            }
            chips.forEach { (language, chip) ->
                val selected = language == languagePref.getValue()
                chip.background = pill(selected)
                chip.setTextColor(
                    if (selected) theme.accentKeyTextColor else theme.keyTextColor
                )
            }
        }

        OutputLanguage.entries.forEach { language ->
            val chip = TextView(ctx).apply {
                text = language.chipLabel
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(12.px(), 4.px(), 12.px(), 4.px())
                setOnClickListener {
                    languagePref.setValue(language)
                    renderSelection()
                }
            }
            chips[language] = chip
            chipRow.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.px() })
        }

        WorkScene.entries.forEach { scene ->
            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val icon = ImageView(ctx).apply {
                    setImageResource(iconFor(scene))
                    layoutParams = LinearLayout.LayoutParams(20.px(), 20.px())
                }
                val name = TextView(ctx).apply {
                    text = scene.label
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
                    scenePref.setValue(scene)
                    renderSelection()
                    popup.dismiss()
                }
            }
            cells[scene] = cell
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

        dismissCurrent()
        sheet.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.width - sheet.measuredWidth) / 2
        val y = -(anchor.height + sheet.measuredHeight)
        popup.showAsDropDown(anchor, x, y, Gravity.START)
        current = popup
        popup.setOnDismissListener { if (current === popup) current = null }
    }
}
