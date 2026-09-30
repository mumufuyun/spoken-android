/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

object Prompts {
    /**
     * Composes the system prompt for a scene × language pair:
     * - DIRECT + ORIGINAL: empty (the caller bypasses AI entirely);
     * - DIRECT + other language: pure translation, no style processing;
     * - scene + ORIGINAL: the scene prompt;
     * - scene + other language: scene prompt plus an output-language instruction.
     */
    fun system(scene: WorkScene, language: OutputLanguage): String = when {
        scene == WorkScene.DIRECT && language == OutputLanguage.ORIGINAL -> ""
        scene == WorkScene.DIRECT -> translationPrompt(language)
        language == OutputLanguage.ORIGINAL -> scenePrompt(scene)
        else -> scenePrompt(scene) + "\n" + languageInstruction(language)
    }

    private fun scenePrompt(scene: WorkScene): String = when (scene) {
        WorkScene.DIRECT -> ""   // handled above; never reached with ORIGINAL

        WorkScene.CHAT -> """
            你是聊天消息整理助手。将用户的口语化输入整理为自然、通顺的聊天消息，
            保留原意和语气，不要过度正式，不添加额外内容。
            直接输出整理后的文字，不加任何说明。
        """.trimIndent()

        WorkScene.WORK -> """
            你是职场沟通助手。将用户的口语化输入整理为清晰、专业、礼貌的工作沟通文字，
            结构清楚、表达准确，保留原意，不添加额外内容。
            直接输出整理后的文字，不加任何说明。
        """.trimIndent()

        WorkScene.FORMAL -> """
            你是正式文稿助手。将用户的口语化输入整理为规范、严谨的书面正式文本，
            用词准确、句式完整、标点规范，保留原意，不添加额外内容。
            直接输出整理后的文字，不加任何说明。
        """.trimIndent()

        WorkScene.MINUTES -> """
            你是会议纪要助手。将用户的口述内容整理为结构化会议纪要，包含：
            【议题】【讨论要点】【决议/待跟进】。
            如信息不完整，只输出已有部分，不要补充猜测内容。
        """.trimIndent()

        WorkScene.SHARE -> """
            你是内容创作助手。将用户的口述内容整理为适合分享发布的内容，
            表达生动、结构清晰、易于阅读，保留原意，不添加额外内容。
            直接输出整理后的文字，不加任何说明。
        """.trimIndent()

        WorkScene.PROMPT -> """
            你是 AI 提示词工程师。将用户的口语化描述转化为清晰、结构化的 AI 指令。
            直接输出提示词，不加任何说明或前缀。
        """.trimIndent()
    }

    private fun languageName(language: OutputLanguage): String = when (language) {
        OutputLanguage.ORIGINAL -> ""
        OutputLanguage.ENGLISH -> "英文"
        OutputLanguage.JAPANESE -> "日文"
        OutputLanguage.KOREAN -> "韩文"
    }

    private fun translationPrompt(language: OutputLanguage): String = """
        你是专业翻译。将用户输入翻译为地道的${languageName(language)}，保持语气和风格。
        只输出译文，不加任何说明。
    """.trimIndent()

    private fun languageInstruction(language: OutputLanguage): String =
        "请将结果输出为${languageName(language)}，只输出该语言的内容。"
}
