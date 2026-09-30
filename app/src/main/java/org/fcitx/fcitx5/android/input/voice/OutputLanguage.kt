/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/**
 * Output language for voice post-processing, decoupled from the scene: any scene can be
 * combined with any language. DIRECT + a non-original language is pure translation.
 */
enum class OutputLanguage(val label: String, val chipLabel: String) {
    ORIGINAL("原语言", "原文"),
    ENGLISH("英文", "EN"),
    JAPANESE("日文", "日"),
    KOREAN("韩文", "韩"),
}
