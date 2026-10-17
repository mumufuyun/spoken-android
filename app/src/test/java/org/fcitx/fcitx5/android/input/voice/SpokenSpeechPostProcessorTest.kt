/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenSpeechPostProcessorTest {
    @Test
    fun preservesAmbiguousNamesInsteadOfReplacingHomophones() {
        assertEquals(
            "请检查阿皮哎和杰森格式",
            SpokenSpeechPostProcessor.process("  请检查阿皮哎和杰森格式  ")
        )
    }

    @Test
    fun collapsesSpacedAcronyms() {
        assertEquals(
            "打开 API 设置",
            SpokenSpeechPostProcessor.process("打开 A P I 设置")
        )
    }

    @Test
    fun preservesOrdinarySpeech() {
        assertEquals(
            "这是普通的一句话。",
            SpokenSpeechPostProcessor.process("这是普通的一句话。")
        )
    }

    @Test
    fun doesNotJoinWordsAcrossLinesOrChangeLowercaseSpeech() {
        val text = "a p i 和优爱克斯\nA B\nC"
        assertEquals(text, SpokenSpeechPostProcessor.process(text))
    }
}
