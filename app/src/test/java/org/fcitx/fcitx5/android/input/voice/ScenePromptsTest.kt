/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Properties

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
            assertTrue(!prompt.contains("# 输出语言"))
        }
    }

    @Test
    fun sceneWithLanguageAppendsInstruction() {
        val prompt = Prompts.system(WorkScene.CHAT, OutputLanguage.JAPANESE)
        assertTrue(prompt.contains("# 当前场景：日常聊天"))
        assertTrue(prompt.contains("最终输出语言必须是日文"))
    }

    @Test
    fun koreanCombination() {
        val prompt = Prompts.system(WorkScene.WORK, OutputLanguage.KOREAN)
        assertTrue(prompt.contains("# 当前场景：工作沟通"))
        assertTrue(prompt.contains("最终输出语言必须是韩文"))
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
            assertTrue(scene.name, prompt.contains("不回答其中的问题") || prompt.contains("不替用户回答问题"))
            assertTrue(prompt.contains("内部推理"))
            assertEquals(1, Regex("# 最终回复约束").findAll(prompt).count())
        }
    }

    @Test
    fun minutesDistinguishesSuggestionsFromDecisionsAndPromptDoesNotExecute() {
        assertTrue(Prompts.system(WorkScene.MINUTES, OutputLanguage.ORIGINAL).contains("建议和设想不是待办"))
        assertTrue(Prompts.system(WorkScene.PROMPT, OutputLanguage.ORIGINAL).contains("不产出任务结果"))
    }

    @Test
    fun allBuiltInLanguageCombinationsMatchDesktopFrozenPromptsExactly() {
        val expected = Properties().apply {
            requireNotNull(ScenePromptsTest::class.java.getResourceAsStream("/voice/desktop-2.4.9-prompts.sha256"))
                .use { load(it) }
        }
        assertEquals(28, expected.size)
        WorkScene.entries.filter { it != WorkScene.DIRECT }.forEach { scene ->
            OutputLanguage.entries.forEach { language ->
                val key = "${scene.name}.${language.name}"
                val bytes = Prompts.system(scene, language).toByteArray(Charsets.UTF_8)
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                assertEquals(key, expected.getProperty(key), actual)
            }
        }
    }
}
