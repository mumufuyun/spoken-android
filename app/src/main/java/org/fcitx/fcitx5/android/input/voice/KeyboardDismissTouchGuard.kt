/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/** A deliberate tap may dismiss; typing spillover and drags must never do so. */
class KeyboardDismissTouchGuard(private val bottomInset: Float, private val touchSlop: Float) {
    private var lastKeyboardTouch: Long? = null
    private var startX = 0f
    private var startY = 0f
    private var width = 0f
    private var safeHeight = 0f
    var isTracking = false
        private set

    fun keyboardTouched(time: Long) {
        lastKeyboardTouch = time
        cancel()
    }

    fun down(x: Float, y: Float, width: Float, height: Float, time: Long) {
        this.width = width
        safeHeight = (height - bottomInset).coerceAtLeast(0f)
        startX = x
        startY = y
        isTracking = contains(x, y) && outsideTypingCooldown(time)
    }

    fun move(x: Float, y: Float) {
        if (!contains(x, y) || (x - startX) * (x - startX) +
            (y - startY) * (y - startY) > touchSlop * touchSlop
        ) cancel()
    }

    fun up(x: Float, y: Float, time: Long): Boolean {
        // Validate Up too: a quick flick may arrive without a separate Move event.
        move(x, y)
        val accepted = isTracking && outsideTypingCooldown(time)
        cancel()
        return accepted
    }

    fun cancel() { isTracking = false }

    private fun contains(x: Float, y: Float) = x >= 0 && x < width && y >= 0 && y < safeHeight

    private fun outsideTypingCooldown(time: Long): Boolean =
        lastKeyboardTouch?.let { time - it >= 300L } ?: true
}
