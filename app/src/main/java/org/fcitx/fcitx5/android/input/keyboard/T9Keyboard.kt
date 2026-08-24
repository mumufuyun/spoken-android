/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
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
        percentWidth = 0.333f
    ),
    setOf(Behavior.Press(KeyAction.VirtualT9KeyAction(keyCode)))
)

/** Touch T9 spike backed by the physical T9 state machine. */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "T9"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                TouchT9DigitKey("@#", "1", KeyEvent.KEYCODE_1),
                TouchT9DigitKey("ABC", "2", KeyEvent.KEYCODE_2),
                TouchT9DigitKey("DEF", "3", KeyEvent.KEYCODE_3)
            ),
            listOf(
                TouchT9DigitKey("GHI", "4", KeyEvent.KEYCODE_4),
                TouchT9DigitKey("JKL", "5", KeyEvent.KEYCODE_5),
                TouchT9DigitKey("MNO", "6", KeyEvent.KEYCODE_6)
            ),
            listOf(
                TouchT9DigitKey("PQRS", "7", KeyEvent.KEYCODE_7),
                TouchT9DigitKey("TUV", "8", KeyEvent.KEYCODE_8),
                TouchT9DigitKey("WXYZ", "9", KeyEvent.KEYCODE_9)
            ),
            listOf(
                LayoutSwitchKey("符号", PickerWindow.Key.Symbol.name, 0.15f, KeyDef.Appearance.Variant.Alternative),
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    0.08f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                LanguageKey(0.12f),
                SpaceKey(),
                BackspaceKey(0.10f),
                ReturnKey()
            )
        )
    }

    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    private var lastIme: InputMethodEntry? = null
    private var t9ModeLabel: String? = null

    private fun updateSpaceText() {
        val ime = lastIme ?: return
        val suffix = t9ModeLabel ?: ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }
        space.mainText.text = buildString {
            append(ime.displayName)
            suffix?.let { append(" ($it)") }
        }
    }

    /**
     * Updates the space bar to show the current T9 mode (中/En/123) so the user can see
     * which mode is active without changing the active Rime schema name.
     */
    fun updateT9ModeLabel(modeLabel: String) {
        t9ModeLabel = modeLabel
        updateSpaceText()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        lastIme = ime
        updateSpaceText()
    }
}
