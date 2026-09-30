/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import androidx.annotation.ColorInt
import org.fcitx.fcitx5.android.input.voice.WorkScene

/**
 * Flat Pop macaron function-key tints (design mockup 15): mint, peach and lemon in light and
 * dark variants. The Theme model carries only one alt-key slot (mint); the remaining tints are
 * applied per key from this table.
 */
object FlatPopTints {
    @ColorInt
    fun mint(dark: Boolean): Int = if (dark) 0xFF24443D.toInt() else 0xFFD9F2EC.toInt()

    @ColorInt
    fun peach(dark: Boolean): Int = if (dark) 0xFF47312A.toInt() else 0xFFFFE8DF.toInt()

    @ColorInt
    fun lemon(dark: Boolean): Int = if (dark) 0xFF413C29.toInt() else 0xFFFFF6D6.toInt()

    /** Scene grid tile tint; null keeps the plain sheet key surface with a hairline border. */
    @ColorInt
    fun sceneTint(scene: WorkScene, dark: Boolean): Int? = when (scene) {
        WorkScene.CHAT -> mint(dark)
        WorkScene.WORK -> peach(dark)
        WorkScene.FORMAL -> lemon(dark)
        WorkScene.MINUTES -> mint(dark)
        WorkScene.SHARE -> peach(dark)
        WorkScene.PROMPT -> lemon(dark)
        WorkScene.DIRECT -> null
    }
}
