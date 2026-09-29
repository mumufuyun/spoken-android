/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

enum class WorkMode(val label: String, val description: String) {
    POLISH(   "润色",       "修正语法、标点，让表达更准确流畅"),
    DIRECT(   "直接输出",   "不经 AI 处理，直接输出识别文字"),
    PROMPT(   "转为提示词", "将口语整理为结构化 AI 指令"),
    TRANSLATE("翻译成英文", "说中文，输出地道英文译文"),
    MINUTES(  "会议纪要",   "整理为结构化纪要（参与人 / 议题 / 决议）"),
    STRUCTURE("结构化整理", "将口述内容整理为要点、待办清单等"),
}
