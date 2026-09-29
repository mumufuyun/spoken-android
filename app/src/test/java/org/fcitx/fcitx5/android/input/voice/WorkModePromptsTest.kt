/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkModePromptsTest {
    @Test
    fun everyModeHasLabelAndDescription() {
        WorkMode.entries.forEach { mode ->
            assertTrue(mode.label.isNotBlank())
            assertTrue(mode.description.isNotBlank())
        }
    }

    @Test
    fun directHasNoPromptOthersDo() {
        assertEquals("", Prompts.system(WorkMode.DIRECT))
        WorkMode.entries.filter { it != WorkMode.DIRECT }.forEach { mode ->
            assertTrue(Prompts.system(mode).isNotBlank())
        }
    }

    @Test
    fun enumNamesRoundTripThroughValueOf() {
        // Preferences persist the enum name; an accidental rename must fail loudly.
        WorkMode.entries.forEach { mode ->
            assertEquals(mode, WorkMode.valueOf(mode.name))
        }
    }

    @Test
    fun asrProviderKindNamesRoundTrip() {
        SpokenAsrProviderKind.entries.forEach { kind ->
            assertEquals(kind, SpokenAsrProviderKind.valueOf(kind.name))
        }
    }
}
