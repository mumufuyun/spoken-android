/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchEnglishInputTest {
    private val input = TouchEnglishInput()

    private fun letter(text: String) = input.map(KeyAction.FcitxKeyAction(text))
    private fun symbol(sym: Int) = input.map(KeyAction.SymAction(KeySym(sym)))

    @Test
    fun lettersAndPunctuationBypassChineseComposition() {
        "qwertyuiopasdfghjklzxcvbnm".forEach {
            assertEquals(KeyAction.EditorTextAction(it.toString()), letter(it.toString()))
        }
        listOf("'", ",", ".", "@", "_", "/", "7").forEach {
            assertEquals(KeyAction.EditorTextAction(it), input.map(KeyAction.CommitAction(it)))
        }
        assertEquals(KeyAction.EditorTextAction(" "), symbol(FcitxKeyMapping.FcitxKey_space))
    }

    @Test
    fun shiftIsConsumedOnlyByALetterAndCapsLockCanBeReleased() {
        assertNull(input.map(KeyAction.CapsAction(false)))
        assertEquals(KeyAction.EditorTextAction("'"), letter("'"))
        assertEquals(KeyAction.EditorTextAction("H"), letter("h"))
        assertEquals(KeyAction.EditorTextAction("i"), letter("i"))
        input.map(KeyAction.CapsAction(true))
        assertEquals(KeyAction.EditorTextAction("A"), letter("a"))
        assertEquals(KeyAction.EditorTextAction("B"), letter("b"))
        input.map(KeyAction.CapsAction(false))
        assertEquals(KeyAction.EditorTextAction("c"), letter("c"))
        input.map(KeyAction.CapsAction(true))
        input.reset()
        assertEquals(KeyAction.EditorTextAction("d"), letter("d"))
    }

    @Test
    fun editingKeysGoToTheEditorAndOtherActionsKeepTheirMeaning() {
        assertEquals(KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DEL), symbol(FcitxKeyMapping.FcitxKey_BackSpace))
        assertEquals(KeyAction.EditorKeyAction(KeyEvent.KEYCODE_ENTER), symbol(FcitxKeyMapping.FcitxKey_Return))
        assertEquals(KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DPAD_LEFT), symbol(FcitxKeyMapping.FcitxKey_Left))
        assertEquals(KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT), symbol(FcitxKeyMapping.FcitxKey_Right))
        assertEquals(KeyAction.SpokenVoiceAction, input.map(KeyAction.SpokenVoiceAction))
        assertEquals(KeyAction.LangSwitchAction, input.map(KeyAction.LangSwitchAction))
    }
}
