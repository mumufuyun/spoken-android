/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.input.voice.WorkScene
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
    fun sceneTilesCycleTintsAndKeepDirectPlain() {
        assertEquals(FlatPopTints.mint(false), FlatPopTints.sceneTint(WorkScene.CHAT, false))
        assertEquals(FlatPopTints.peach(false), FlatPopTints.sceneTint(WorkScene.WORK, false))
        assertEquals(FlatPopTints.lemon(false), FlatPopTints.sceneTint(WorkScene.FORMAL, false))
        assertEquals(FlatPopTints.mint(false), FlatPopTints.sceneTint(WorkScene.MINUTES, false))
        assertEquals(FlatPopTints.peach(false), FlatPopTints.sceneTint(WorkScene.SHARE, false))
        assertEquals(FlatPopTints.lemon(false), FlatPopTints.sceneTint(WorkScene.PROMPT, false))
        assertNull(FlatPopTints.sceneTint(WorkScene.DIRECT, false))
    }
}

