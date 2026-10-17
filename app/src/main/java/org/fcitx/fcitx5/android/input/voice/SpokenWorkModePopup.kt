/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import splitties.dimensions.dp

/** Keyboard-aligned scene/language sheet. Settings apply to the next voice session. */
object SpokenWorkModePopup {

    private var current: PopupWindow? = null

    /** Dismiss on keyboard detach so the popup never outlives its window token. */
    fun dismissCurrent() {
        current?.dismiss()
        current = null
    }

    @StringRes
    private fun summaryFor(scene: WorkScene): Int = when (scene) {
        WorkScene.DIRECT -> R.string.voice_picker_direct
        WorkScene.SMOOTH -> R.string.voice_picker_smooth
        WorkScene.CHAT -> R.string.voice_picker_chat
        WorkScene.WORK -> R.string.voice_picker_work
        WorkScene.FORMAL -> R.string.voice_picker_formal
        WorkScene.MINUTES -> R.string.voice_picker_minutes
        WorkScene.SHARE -> R.string.voice_picker_share
        WorkScene.PROMPT -> R.string.voice_picker_prompt
    }

    fun show(
        anchor: View,
        keyboard: View,
        theme: Theme,
        onCancel: (() -> Unit)? = null,
        onRecover: (() -> Unit)? = null
    ) {
        if (!anchor.isAttachedToWindow || !keyboard.isShown || keyboard.width <= 0 || keyboard.height <= 0) {
            return
        }
        val ctx = anchor.context
        fun Int.px() = ctx.dp(this)
        val location = IntArray(2).also(keyboard::getLocationOnScreen)
        val keyboardBounds = Rect(
            location[0], location[1], location[0] + keyboard.width, location[1] + keyboard.height
        )
        val display = ctx.resources.displayMetrics
        val insets = ViewCompat.getRootWindowInsets(keyboard)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val safeLeft = maxOf(keyboardBounds.left, insets?.left ?: 0) + 12.px()
        val safeRight = minOf(keyboardBounds.right, display.widthPixels - (insets?.right ?: 0)) - 12.px()
        val safeTop = (insets?.top ?: 0) + 8.px()
        val bottom = minOf(keyboardBounds.bottom, display.heightPixels - (insets?.bottom ?: 0)) - 8.px()
        val sheetWidth = minOf(520.px(), safeRight - safeLeft)
        val maxHeight = minOf(bottom - safeTop, (display.heightPixels * 0.65f).toInt())
        if (sheetWidth <= 0 || maxHeight <= 0) return

        val voicePrefs = AppPrefs.getInstance().voice
        val scenePref = voicePrefs.defaultScene
        val languagePref = voicePrefs.outputLanguage
        val cells = mutableMapOf<WorkScene, LinearLayout>()
        val chips = mutableMapOf<OutputLanguage, TextView>()
        lateinit var popup: PopupWindow

        fun surface(selected: Boolean) = RippleDrawable(
            ColorStateList.valueOf(theme.keyPressHighlightColor),
            GradientDrawable().apply {
                cornerRadius = 12.px().toFloat()
                setColor(if (selected) theme.accentKeyBackgroundColor else theme.keyBackgroundColor)
                setStroke(1.px(), if (selected) theme.accentKeyBackgroundColor else theme.dividerColor)
            },
            null
        )

        fun label(value: CharSequence, size: Float, color: Int, bold: Boolean = false) =
            TextView(ctx).apply {
                text = value
                textSize = size
                setTextColor(color)
                InputUiFont.applyTo(this, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

        fun renderSelection() {
            cells.forEach { (scene, cell) ->
                val selected = scene == scenePref.getValue()
                val translationOnly = scene == WorkScene.DIRECT && languagePref.getValue() != OutputLanguage.ORIGINAL
                cell.isSelected = selected
                cell.contentDescription = scene.label + "：" + if (translationOnly)
                    ctx.getString(R.string.voice_picker_translate) else scene.description
                cell.background = surface(selected)
                val foreground = if (selected) theme.accentKeyTextColor else theme.keyTextColor
                val titleRow = cell.getChildAt(0) as LinearLayout
                (titleRow.getChildAt(0) as TextView).setTextColor(foreground)
                (titleRow.getChildAt(1) as ImageView).apply {
                    setColorFilter(foreground)
                    visibility = if (selected) View.VISIBLE else View.INVISIBLE
                }
                (cell.getChildAt(1) as TextView).apply {
                    setTextColor(if (selected) foreground else theme.candidateCommentColor)
                    setText(
                        if (translationOnly) R.string.voice_picker_translate else summaryFor(scene)
                    )
                }
            }
            chips.forEach { (language, chip) ->
                val selected = language == languagePref.getValue()
                chip.isSelected = selected
                chip.background = surface(selected)
                chip.setTextColor(if (selected) theme.accentKeyTextColor else theme.keyTextColor)
            }
        }

        val header = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.px(), 0, 0, 0)
            addView(
                label(ctx.getString(R.string.voice_picker_title), 17f, theme.keyTextColor, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(label(ctx.getString(R.string.done), 14f, theme.keyTextColor, true).apply {
                gravity = Gravity.CENTER
                minimumHeight = 48.px()
                isFocusable = true
                background = surface(false)
                setOnClickListener { popup.dismiss() }
            }, LinearLayout.LayoutParams(64.px(), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.px(), 0, 4.px())
            addView(
                label(ctx.getString(R.string.output_language), 12f, theme.candidateCommentColor),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.px() }
            )
        }
        val languageRow = LinearLayout(ctx)
        OutputLanguage.entries.forEachIndexed { index, language ->
            val chip = label(language.label, 13f, theme.keyTextColor).apply {
                gravity = Gravity.CENTER
                minimumHeight = 44.px()
                setPadding(2.px(), 4.px(), 2.px(), 4.px())
                isFocusable = true
                setOnClickListener {
                    languagePref.setValue(language)
                    renderSelection()
                }
            }
            chips[language] = chip
            languageRow.addView(chip, LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (index > 0) marginStart = 6.px()
            })
        }
        body.addView(languageRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16.px() })

        WorkScene.entries.chunked(2).forEachIndexed { rowIndex, scenes ->
            val row = LinearLayout(ctx)
            scenes.forEachIndexed { columnIndex, scene ->
                val titleRow = LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(scene.label, 14f, theme.keyTextColor, true).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_baseline_check_24)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(18.px(), 18.px()).apply { marginStart = 4.px() })
                }
                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = 60.px()
                    setPadding(12.px(), 8.px(), 10.px(), 8.px())
                    isFocusable = true
                    addView(titleRow, LinearLayout.LayoutParams(-1, -2))
                    addView(label(ctx.getString(summaryFor(scene)), 11f, theme.candidateCommentColor).apply {
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 3.px() })
                    setOnClickListener {
                        scenePref.setValue(scene)
                        popup.dismiss()
                    }
                }
                cells[scene] = cell
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (columnIndex > 0) marginStart = 8.px()
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowIndex > 0) topMargin = 8.px()
            })
        }
        renderSelection()

        fun addAction(@StringRes text: Int, action: () -> Unit) {
            body.addView(label(ctx.getString(text), 13f, theme.keyTextColor).apply {
                gravity = Gravity.CENTER
                minimumHeight = 44.px()
                isFocusable = true
                setPadding(8.px(), 6.px(), 8.px(), 6.px())
                background = surface(false)
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.px() })
        }
        onCancel?.let { addAction(R.string.voice_picker_cancel, it) }
        onRecover?.let { addAction(R.string.voice_picker_recover, it) }
        body.addView(label(
            ctx.getString(if (onCancel != null) R.string.voice_picker_next_session else R.string.voice_picker_hint),
            11f,
            theme.candidateCommentColor
        ).apply { setPadding(4.px(), 12.px(), 4.px(), 0) })

        // Keep the header reachable when a small window or large text requires scrolling.
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.px(), 10.px(), 12.px(), 12.px())
            background = GradientDrawable().apply {
                cornerRadius = 18.px().toFloat()
                setColor(theme.popupBackgroundColor)
                setStroke(1.px(), theme.dividerColor)
            }
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(ScrollView(ctx).apply {
                isFillViewport = false
                clipToPadding = false
                addView(body)
            }, LinearLayout.LayoutParams(-1, -2, 1f))
        }
        sheet.measure(
            View.MeasureSpec.makeMeasureSpec(sheetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )
        popup = PopupWindow(sheet, sheetWidth, sheet.measuredHeight, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            // Use screen coordinates, independent of the dock/voice button that opened us.
            setIsLaidOutInScreen(true)
            elevation = 0f
        }

        val onLayoutChange = View.OnLayoutChangeListener { _, left, top, right, bottomEdge,
                oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottomEdge != oldBottom) {
                popup.dismiss()
            }
        }
        popup.setOnDismissListener {
            keyboard.removeOnLayoutChangeListener(onLayoutChange)
            if (current === popup) current = null
        }
        dismissCurrent()
        current = popup
        popup.showAtLocation(
            keyboard,
            Gravity.TOP or Gravity.LEFT,
            safeLeft + (safeRight - safeLeft - sheetWidth) / 2,
            bottom - sheet.measuredHeight
        )
        keyboard.addOnLayoutChangeListener(onLayoutChange)
    }
}
