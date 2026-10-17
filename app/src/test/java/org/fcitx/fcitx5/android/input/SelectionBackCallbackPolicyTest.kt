/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionBackCallbackPolicyTest {
    @Test
    fun `hidden ime never registers callback`() {
        assertFalse(
            SelectionBackCallbackPolicy.shouldRegister(
                hasSelection = true,
                imeWindowVisible = false,
                isVirtualKeyboard = false,
                hasPhysicalKeyboard = true
            )
        )
    }

    @Test
    fun `touch keyboard never registers callback`() {
        assertFalse(
            SelectionBackCallbackPolicy.shouldRegister(
                hasSelection = true,
                imeWindowVisible = true,
                isVirtualKeyboard = true,
                hasPhysicalKeyboard = true
            )
        )
    }

    @Test
    fun `visible physical keyboard selection can register callback`() {
        assertTrue(
            SelectionBackCallbackPolicy.shouldRegister(
                hasSelection = true,
                imeWindowVisible = true,
                isVirtualKeyboard = false,
                hasPhysicalKeyboard = true
            )
        )
    }

    @Test
    fun `empty selection never registers callback`() {
        assertFalse(
            SelectionBackCallbackPolicy.shouldRegister(
                hasSelection = false,
                imeWindowVisible = true,
                isVirtualKeyboard = false,
                hasPhysicalKeyboard = true
            )
        )
    }

    @Test
    fun `touch T9 using the physical engine path must not intercept navigation`() {
        assertFalse(
            SelectionBackCallbackPolicy.shouldRegister(
                hasSelection = true,
                imeWindowVisible = true,
                isVirtualKeyboard = false,
                hasPhysicalKeyboard = false
            )
        )
    }
}
