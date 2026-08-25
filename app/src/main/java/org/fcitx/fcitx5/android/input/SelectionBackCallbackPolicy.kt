/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

internal object SelectionBackCallbackPolicy {
    fun shouldRegister(
        hasSelection: Boolean,
        imeWindowVisible: Boolean,
        isVirtualKeyboard: Boolean
    ): Boolean = hasSelection && imeWindowVisible && !isVirtualKeyboard
}
