/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.input.voice.WorkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlatPopTintsTest {
    @Test
    fun lightAndDarkPalettesMatchMockup() {
        assertEquals(0xFFD9F2EC.toInt(), FlatPopTints.mint(dark = false))
        assertEquals(0xFFFFE8DF.toInt(), FlatPopTints.peach(dark = false))
        assertEquals(0xFFFFF6D6.toInt(), FlatPopTints.lemon(dark = false))
        assertEquals(0xFF24443D.toInt(), FlatPopTints.mint(dark = true))
        assertEquals(0xFF47312A.toInt(), FlatPopTints.peach(dark = true))
        assertEquals(0xFF413C29.toInt(), FlatPopTints.lemon(dark = true))
    }

    @Test
    fun modeTilesFollowMockupAssignment() {
        assertEquals(FlatPopTints.mint(false), FlatPopTints.modeTint(WorkMode.POLISH, false))
        assertEquals(FlatPopTints.lemon(false), FlatPopTints.modeTint(WorkMode.PROMPT, false))
        assertEquals(FlatPopTints.peach(false), FlatPopTints.modeTint(WorkMode.TRANSLATE, false))
        assertEquals(FlatPopTints.mint(false), FlatPopTints.modeTint(WorkMode.STRUCTURE, false))
        assertNull(FlatPopTints.modeTint(WorkMode.DIRECT, false))
        assertNull(FlatPopTints.modeTint(WorkMode.MINUTES, false))
    }
}
