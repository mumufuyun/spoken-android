/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

object Prompts {
    fun system(mode: WorkMode): String = when (mode) {
        WorkMode.DIRECT -> ""   // no AI call in direct mode

        WorkMode.POLISH -> """
            你是专业文字润色助手。将用户的口语化输入润色为书面语，
            修正语法错误和标点，保留原意，不添加额外内容。
            直接输出润色后的文字，不加任何说明。
        """.trimIndent()

        WorkMode.PROMPT -> """
            你是 AI 提示词工程师。将用户的口语化描述转化为清晰、结构化的 AI 指令。
            直接输出提示词，不加任何说明或前缀。
        """.trimIndent()

        WorkMode.TRANSLATE -> """
            你是专业翻译。将用户输入翻译成地道的英文，保持语气和风格。
            只输出英文译文，不加任何说明。
        """.trimIndent()

        WorkMode.MINUTES -> """
            你是会议纪要助手。将用户的口述内容整理为结构化会议纪要，包含：
            【参与人】【议题】【讨论要点】【决议/待跟进】。
            如信息不完整，只输出已有部分，不要补充猜测内容。
        """.trimIndent()

        WorkMode.STRUCTURE -> """
            你是内容整理助手。将用户的口述内容整理为清晰的要点列表或待办清单。
            使用「·」作为条目符号，直接输出，不加说明。
        """.trimIndent()
    }
}
