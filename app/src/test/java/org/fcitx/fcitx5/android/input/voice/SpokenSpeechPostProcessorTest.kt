/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenSpeechPostProcessorTest {
    @Test
    fun appliesOnlyHighConfidenceTechnicalTerms() {
        assertEquals(
            "请检查API和JSON格式",
            SpokenSpeechPostProcessor.process("  请检查阿皮哎和杰森格式  ")
        )
    }

    @Test
    fun collapsesSpacedAcronyms() {
        assertEquals(
            "打开API设置",
            SpokenSpeechPostProcessor.process("打开 a p i 设置")
        )
    }

    @Test
    fun preservesOrdinarySpeech() {
        assertEquals(
            "这是普通的一句话。",
            SpokenSpeechPostProcessor.process("这是普通的一句话。")
        )
    }
}
