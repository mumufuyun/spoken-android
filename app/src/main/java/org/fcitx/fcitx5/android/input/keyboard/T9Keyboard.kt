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
        border = Border.On
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
        viewId = R.id.button_lang
    ),
    setOf(Behavior.Press(KeyAction.LangSwitchAction))
)

private class TouchT9VoiceKey : KeyDef(
    Appearance.Text(
        displayText = "点击说话",
        textSize = 14f,
        textStyle = Typeface.BOLD,
        variant = Variant.Accent,
        border = Border.On
    ),
    emptySet(),
    emptyArray()
)

/**
 * Touch-first T9 layout built on a stable four-row grid.
 *
 * Punctuation and digits occupy the first three rows. Bottom controls occupy row four. Delete
 * aligns with digit row one, the voice target spans digit rows two and three, and Return aligns
 * with the bottom controls. The voice key stays presentation-only until the in-process Spoken
 * voice pipeline replaces the legacy external-IME handoff.
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, emptyList()) {

    companion object {
        const val Name = "T9"
    }

    private fun horizontalRow(definitions: List<KeyDef>): ConstraintLayout = constraintLayout {
        val views = definitions.map(::createKeyView)
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
        val views = definitions.map(::createKeyView)
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
            SymbolKey(",", variant = Variant.Alternative, border = Border.On),
            SymbolKey(".", variant = Variant.Alternative, border = Border.On),
            SymbolKey("?", variant = Variant.Alternative, border = Border.On),
            SymbolKey("!", variant = Variant.Alternative, border = Border.On)
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
                Border.On
            ),
            LayoutSwitchKey("123", NumberKeyboard.Name, 0.15f, Variant.Normal, Border.On),
            SpaceKey(0.52f, Border.On),
            TouchT9ModeKey()
        )
    )

    private val backspace = createKeyView(BackspaceKey(border = Border.On))
    private val voice = createKeyView(TouchT9VoiceKey())
    private val returnKey = createKeyView(ReturnKey(border = Border.On))

    init {
        add(punctuationRail, lParams {
            leftOfParent()
            topOfParent()
            above(bottomRow)
            matchConstraintPercentWidth = 0.15f
        })
        add(digitSurface, lParams {
            leftToRightOf(punctuationRail)
            rightToLeftOf(backspace)
            topOfParent()
            above(bottomRow)
        })
        add(backspace, lParams {
            rightOfParent()
            topOfParent()
            matchConstraintPercentWidth = 0.16f
            matchConstraintPercentHeight = 0.25f
        })
        add(voice, lParams {
            rightOfParent()
            below(backspace)
            above(returnKey)
            matchConstraintPercentWidth = 0.16f
        })
        add(bottomRow, lParams {
            leftOfParent()
            rightToLeftOf(returnKey)
            bottomOfParent()
            matchConstraintPercentHeight = 0.25f
        })
        add(returnKey, lParams {
            rightOfParent()
            bottomOfParent()
            matchConstraintPercentWidth = 0.16f
            matchConstraintPercentHeight = 0.25f
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
