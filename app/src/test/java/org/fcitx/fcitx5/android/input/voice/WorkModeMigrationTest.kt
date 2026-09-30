/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkModeMigrationTest {
    @Test
    fun legacyModesMapToSceneAndLanguage() {
        assertEquals(
            WorkScene.DIRECT to OutputLanguage.ORIGINAL,
            WorkModeMigration.migrate("DIRECT")
        )
        assertEquals(
            WorkScene.PROMPT to OutputLanguage.ORIGINAL,
            WorkModeMigration.migrate("PROMPT")
        )
        assertEquals(
            WorkScene.MINUTES to OutputLanguage.ORIGINAL,
            WorkModeMigration.migrate("MINUTES")
        )
        assertEquals(
            WorkScene.WORK to OutputLanguage.ORIGINAL,
            WorkModeMigration.migrate("POLISH")
        )
        assertEquals(
            WorkScene.FORMAL to OutputLanguage.ORIGINAL,
            WorkModeMigration.migrate("STRUCTURE")
        )
        assertEquals(
            WorkScene.DIRECT to OutputLanguage.ENGLISH,
            WorkModeMigration.migrate("TRANSLATE")
        )
    }

    @Test
    fun unknownValuesAreDropped() {
        assertNull(WorkModeMigration.migrate("NOPE"))
        assertNull(WorkModeMigration.migrate(""))
    }
}
