/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping

/** Touch QWERTY is literal input; physical T9 keeps its separate predictive English path. */
class TouchEnglishInput {
    enum class Caps { NONE, ONCE, LOCK }

    var caps = Caps.NONE
        private set

    fun reset() {
        caps = Caps.NONE
    }

    fun map(action: KeyAction): KeyAction? = when (action) {
        is KeyAction.CapsAction -> {
            caps = if (action.lock) {
                if (caps == Caps.LOCK) Caps.NONE else Caps.LOCK
            } else {
                if (caps == Caps.NONE) Caps.ONCE else Caps.NONE
            }
            null
        }
        is KeyAction.FcitxKeyAction -> {
            val isLetter = action.act.length == 1 && action.act[0].lowercaseChar() in 'a'..'z'
            val text = if (isLetter) {
                if (caps == Caps.NONE) action.act.lowercase() else action.act.uppercase()
            } else action.act
            if (isLetter && caps == Caps.ONCE) caps = Caps.NONE
            KeyAction.EditorTextAction(text)
        }
        is KeyAction.CommitAction -> KeyAction.EditorTextAction(action.text)
        is KeyAction.SymAction -> when (action.sym.sym) {
            FcitxKeyMapping.FcitxKey_space -> KeyAction.EditorTextAction(" ")
            FcitxKeyMapping.FcitxKey_BackSpace -> KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DEL)
            FcitxKeyMapping.FcitxKey_Return -> KeyAction.EditorKeyAction(KeyEvent.KEYCODE_ENTER)
            FcitxKeyMapping.FcitxKey_Left -> KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DPAD_LEFT)
            FcitxKeyMapping.FcitxKey_Right -> KeyAction.EditorKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT)
            else -> action
        }
        else -> action
    }
}
