/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.core.ScancodeMapping
import org.fcitx.fcitx5.android.input.picker.PickerWindow

sealed class KeyAction {

    data class FcitxKeyAction(
        val act: String,
        val code: Int = ScancodeMapping.charToScancode(act[0]),
        val states: KeyStates = KeyStates.Virtual
    ) : KeyAction()

    data class SymAction(val sym: KeySym, val states: KeyStates = KeyStates.Virtual) : KeyAction()

    data class CommitAction(val text: String) : KeyAction()

    data class CapsAction(val lock: Boolean) : KeyAction()

    data object QuickPhraseAction : KeyAction()

    data object UnicodeAction : KeyAction()

    data object LangSwitchAction : KeyAction()

    data object ShowInputMethodPickerAction : KeyAction()

    data class LayoutSwitchAction(val act: String = "") : KeyAction()

    data class MoveSelectionAction(val start: Int = 0, val end: Int = 0) : KeyAction()

    data class DeleteSelectionAction(val totalCnt: Int = 0) : KeyAction()

    data class PickerSwitchAction(val key: PickerWindow.Key? = null) : KeyAction()

    data object SpaceLongPressAction : KeyAction()

    data class PasswordPeekAction(val enabled: Boolean) : KeyAction()

    /** A touch key that must use the same short-press semantics as a physical T9 key. */
    data class VirtualT9KeyAction(val keyCode: Int) : KeyAction()

    /** Selects one of the pinyin readings mirrored into the touch keyboard's left rail. */
    data class T9ReadingSlotAction(val slot: Int) : KeyAction()

    /** Starts or finishes Spoken's in-keyboard voice recognition session. */
    data object SpokenVoiceAction : KeyAction()

    /** Opens the Spoken scene-mode picker (long press on the voice key). */
    data object SpokenVoiceModeAction : KeyAction()

    /** Switches an in-keyboard Spoken touch panel without replacing the keyboard window. */
    data class TouchPanelAction(val panel: String) : KeyAction()
}
