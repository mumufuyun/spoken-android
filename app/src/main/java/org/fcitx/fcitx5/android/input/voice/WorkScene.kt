/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/**
 * Voice post-processing scenes from the MVP spec (docs/spoken/SPOKEN_MOBILE_MVP.md 4.4).
 * DIRECT with the original language bypasses AI; SMOOTH opts into light editing.
 */
enum class WorkScene(val label: String, val description: String) {
    DIRECT("原样转写", "不经 AI 处理，直接输出识别文字"),
    SMOOTH("流畅转写", "去停顿、理顺语句，保留原意、顺序和语气"),
    CHAT("日常聊天", "整理为自然通顺的聊天消息，保留语气"),
    WORK("工作沟通", "整理为清晰、专业、礼貌的工作表达"),
    FORMAL("正式材料", "整理为规范严谨的正式书面文本"),
    MINUTES("会议记录", "区分讨论、结论、待办和待确认事项"),
    SHARE("内容分享", "整理为适合发布的分享内容"),
    PROMPT("AI 指令", "将口语整理为结构化 AI 提示词"),
}
