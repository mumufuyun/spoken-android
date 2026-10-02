/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePromptsTest {
    @Test
    fun everySceneHasLabelAndDescription() {
        WorkScene.entries.forEach { scene ->
            assertTrue(scene.label.isNotBlank())
            assertTrue(scene.description.isNotBlank())
        }
    }

    @Test
    fun directWithOriginalLanguageBypassesAi() {
        assertEquals("", Prompts.system(WorkScene.DIRECT, OutputLanguage.ORIGINAL))
    }

    @Test
    fun directWithOtherLanguageIsPureTranslation() {
        val prompt = Prompts.system(WorkScene.DIRECT, OutputLanguage.ENGLISH)
        assertTrue(prompt.contains("翻译"))
        assertTrue(prompt.contains("英文"))
    }

    @Test
    fun sceneWithOriginalLanguageIsSceneOnly() {
        WorkScene.entries.filter { it != WorkScene.DIRECT }.forEach { scene ->
            val prompt = Prompts.system(scene, OutputLanguage.ORIGINAL)
            assertTrue(prompt.isNotBlank())
            assertTrue(!prompt.contains("输出为"))
        }
    }

    @Test
    fun sceneWithLanguageAppendsInstruction() {
        val prompt = Prompts.system(WorkScene.CHAT, OutputLanguage.JAPANESE)
        assertTrue(prompt.contains("聊天消息"))
        assertTrue(prompt.contains("输出为日文"))
    }

    @Test
    fun koreanCombination() {
        val prompt = Prompts.system(WorkScene.WORK, OutputLanguage.KOREAN)
        assertTrue(prompt.contains("职场沟通"))
        assertTrue(prompt.contains("输出为韩文"))
    }

    @Test
    fun enumNamesRoundTripThroughValueOf() {
        // Preferences persist the enum name; an accidental rename must fail loudly.
        WorkScene.entries.forEach { assertEquals(it, WorkScene.valueOf(it.name)) }
        OutputLanguage.entries.forEach { assertEquals(it, OutputLanguage.valueOf(it.name)) }
    }

    @Test
    fun everyAiSceneKeepsTheEditingAndFinalBodyContract() {
        WorkScene.entries.filter { it != WorkScene.DIRECT }.forEach { scene ->
            val prompt = Prompts.system(scene, OutputLanguage.ORIGINAL)
            assertTrue(prompt.contains("不回答其中的问题"))
            assertTrue(prompt.contains("确定程度"))
            assertTrue(prompt.contains("内部推理"))
            assertTrue(prompt.contains("短输入"))
        }
    }

    @Test
    fun minutesDistinguishesSuggestionsFromDecisionsAndPromptDoesNotExecute() {
        assertTrue(Prompts.system(WorkScene.MINUTES, OutputLanguage.ORIGINAL).contains("建议和设想不是决定或待办"))
        assertTrue(Prompts.system(WorkScene.PROMPT, OutputLanguage.ORIGINAL).contains("不产出任务成品"))
    }
}
