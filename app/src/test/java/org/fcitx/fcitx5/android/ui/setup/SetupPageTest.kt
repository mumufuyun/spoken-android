/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SetupPageTest {
    @Test
    fun voicePagesDoNotBlockWizardPopup() {
        assertEquals(listOf(SetupPage.Enable, SetupPage.Select), SetupPage.blockingPages)
        assertFalse(SetupPage.Microphone in SetupPage.blockingPages)
        assertFalse(SetupPage.VoiceConfig in SetupPage.blockingPages)
    }

    @Test
    fun pageOrderKeepsOriginalStepsFirst() {
        assertEquals(
            listOf(
                SetupPage.Enable,
                SetupPage.Select,
                SetupPage.Microphone,
                SetupPage.VoiceConfig
            ),
            SetupPage.entries
        )
    }
}
