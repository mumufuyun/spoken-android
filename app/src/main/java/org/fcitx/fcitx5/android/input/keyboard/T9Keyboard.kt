/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.constraintlayout.widget.ConstraintLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.imageResource
import splitties.views.padding

private class TouchT9DigitKey(
    letters: String,
    digit: String,
    keyCode: Int
) : KeyDef(
    Appearance.AltText(
        displayText = letters,
        altText = digit,
        textSize = 23f,
        altTextSize = 12f,
        percentWidth = 0.333f,
        border = Border.On,
        margin = false
    ),
    setOf(Behavior.Press(KeyAction.VirtualT9KeyAction(keyCode)))
)

private class TouchT9ModeKey : KeyDef(
    Appearance.Text(
        displayText = "中/英",
        textSize = 14f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.15f,
        variant = Variant.Normal,
        border = Border.On,
        margin = false,
        viewId = R.id.button_lang
    ),
    setOf(Behavior.Press(KeyAction.LangSwitchAction))
)

private class TouchT9VoiceKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_spoken_waveform_36,
        variant = Variant.Accent,
        border = Border.On,
        margin = false
    ),
    emptySet(),
    emptyArray()
)

/**
 * Touch-first T9 layout built on a stable four-row grid.
 *
 * Punctuation and digits occupy the first three rows. Bottom controls occupy row four. Delete
 * aligns with digit row one, the voice target spans digit rows two and three, and Return aligns
 * with the bottom controls. The waveform key stays presentation-only until the in-process Spoken
 * voice pipeline replaces the legacy external-IME handoff.
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, emptyList()) {

    companion object {
        const val Name = "T9"
        private const val TOUCH_KEY_GAP_DP = 2
        private const val TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP = 2
        private const val TOUCH_BOTTOM_ROW_HEIGHT_DP = 48
    }

    private fun createTouchKeyView(def: KeyDef): KeyView = createKeyView(def).apply {
        // Keep the whole cell tappable while reducing the visible gap between adjacent keycaps.
        padding = dp(TOUCH_KEY_GAP_DP)
    }

    private fun createCompactBottomKeyView(def: KeyDef): KeyView = createKeyView(def).apply {
        setPadding(
            dp(TOUCH_KEY_GAP_DP),
            dp(TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP),
            dp(TOUCH_KEY_GAP_DP),
            dp(TOUCH_BOTTOM_KEY_VERTICAL_INSET_DP)
        )
    }

    private fun horizontalRow(
        definitions: List<KeyDef>,
        compactBottomKeycaps: Boolean = false
    ): ConstraintLayout = constraintLayout {
        val views = definitions.map { definition ->
            if (compactBottomKeycaps && definition !is SpaceKey) {
                createCompactBottomKeyView(definition)
            } else {
                createTouchKeyView(definition)
            }
        }
        views.forEachIndexed { index, view ->
            add(view, lParams {
                centerVertically()
                if (index == 0) {
                    leftOfParent()
                    horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    leftToRightOf(views[index - 1])
                }
                if (index == views.lastIndex) {
                    rightOfParent()
                } else {
                    rightToLeftOf(views[index + 1])
                }
                matchConstraintPercentWidth = definitions[index].appearance.percentWidth
            })
        }
    }

    private fun verticalColumn(definitions: List<KeyDef>): ConstraintLayout = constraintLayout {
        val views = definitions.map(::createTouchKeyView)
        views.forEachIndexed { index, view ->
            add(view, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(views[index - 1])
                }
                if (index == views.lastIndex) {
                    bottomOfParent()
                } else {
                    above(views[index + 1])
                }
                matchConstraintPercentHeight = 1f / definitions.size
            })
        }
    }

    private val punctuationRail = verticalColumn(
        listOf(
            SymbolKey(",", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey(".", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey("?", variant = Variant.Alternative, border = Border.On, margin = false),
            SymbolKey("!", variant = Variant.Alternative, border = Border.On, margin = false)
        )
    )

    private val digitRows = listOf(
        horizontalRow(
            listOf(
                TouchT9DigitKey("@#", "1", KeyEvent.KEYCODE_1),
                TouchT9DigitKey("ABC", "2", KeyEvent.KEYCODE_2),
                TouchT9DigitKey("DEF", "3", KeyEvent.KEYCODE_3)
            )
        ),
        horizontalRow(
            listOf(
                TouchT9DigitKey("GHI", "4", KeyEvent.KEYCODE_4),
                TouchT9DigitKey("JKL", "5", KeyEvent.KEYCODE_5),
                TouchT9DigitKey("MNO", "6", KeyEvent.KEYCODE_6)
            )
        ),
        horizontalRow(
            listOf(
                TouchT9DigitKey("PQRS", "7", KeyEvent.KEYCODE_7),
                TouchT9DigitKey("TUV", "8", KeyEvent.KEYCODE_8),
                TouchT9DigitKey("WXYZ", "9", KeyEvent.KEYCODE_9)
            )
        )
    )

    private val digitSurface = constraintLayout {
        digitRows.forEachIndexed { index, row ->
            add(row, lParams {
                centerHorizontally()
                if (index == 0) {
                    topOfParent()
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                } else {
                    below(digitRows[index - 1])
                }
                if (index == digitRows.lastIndex) {
                    bottomOfParent()
                } else {
                    above(digitRows[index + 1])
                }
                matchConstraintPercentHeight = 1f / digitRows.size
            })
        }
    }

    private val bottomRow = horizontalRow(
        listOf(
            LayoutSwitchKey(
                "符号",
                PickerWindow.Key.Symbol.name,
                0.18f,
                Variant.Alternative,
                Border.On,
                margin = false
            ),
            LayoutSwitchKey("123", NumberKeyboard.Name, 0.15f, Variant.Normal, Border.On, margin = false),
            SpaceKey(0.52f, Border.On, margin = false),
            TouchT9ModeKey()
        ),
        compactBottomKeycaps = true
    )

    private val backspace = createTouchKeyView(BackspaceKey(border = Border.On, margin = false))
    private val voice = createTouchKeyView(TouchT9VoiceKey())
    private val returnKey = createTouchKeyView(ReturnKey(border = Border.On, margin = false))

    private val rightActionSurface = constraintLayout {
        add(backspace, lParams {
            leftOfParent()
            rightOfParent()
            topOfParent()
            matchConstraintPercentWidth = 1f
            matchConstraintPercentHeight = 1f / 3f
        })
        add(voice, lParams {
            leftOfParent()
            rightOfParent()
            below(backspace)
            bottomOfParent()
            matchConstraintPercentWidth = 1f
        })
    }

    init {
        add(punctuationRail, lParams {
            leftOfParent()
            topOfParent()
            above(bottomRow)
            matchConstraintPercentWidth = 0.15f
        })
        add(digitSurface, lParams {
            leftToRightOf(punctuationRail)
            rightToLeftOf(rightActionSurface)
            topOfParent()
            above(bottomRow)
        })
        add(rightActionSurface, lParams {
            rightOfParent()
            topOfParent()
            above(returnKey)
            matchConstraintPercentWidth = 0.16f
        })
        add(bottomRow, lParams {
            leftOfParent()
            rightToLeftOf(returnKey)
            bottomOfParent()
            height = dp(TOUCH_BOTTOM_ROW_HEIGHT_DP)
        })
        add(returnKey, lParams {
            rightOfParent()
            bottomOfParent()
            matchConstraintPercentWidth = 0.16f
            height = dp(TOUCH_BOTTOM_ROW_HEIGHT_DP)
        })
    }

    private val mode: TextKeyView by lazy { findViewById(R.id.button_lang) }
    private val returnView: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        returnView.img.imageResource = returnDrawable
    }

    fun updateT9ModeLabel(modeLabel: String) {
        mode.mainText.text = modeLabel
    }
}
