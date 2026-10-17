/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** A recording indicator, not an audio-level meter. One animator drives all seven bars. */
class SpokenVoiceKeyDrawable(@ColorInt color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = 3.4f
        strokeCap = Paint.Cap.ROUND
    }
    private var recording = false
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1_200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun setRecording(recording: Boolean) {
        // Partial transcripts repeatedly publish Recording; they must not restart the wave.
        if (this.recording == recording) return
        this.recording = recording
        updateAnimation()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        updateAnimation()
        return changed
    }

    private fun updateAnimation() {
        val motionEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
        if (recording && isVisible && motionEnabled) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            phase = 0f
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val scale = min(bounds.width() / 40f, bounds.height() / 36f)
        if (scale <= 0f) return
        val checkpoint = canvas.save()
        canvas.translate(bounds.exactCenterX() - 20f * scale, bounds.exactCenterY() - 18f * scale)
        canvas.scale(scale, scale)
        for (index in 0..6) {
            val envelope = 1f - abs(index - 3) / 3f * .45f
            val height = if (animator.isStarted) {
                val wave = (sin(phase + index * .85f) + 1f) / 2f
                7f + 23f * envelope * wave
            } else {
                IDLE_HEIGHTS[index]
            }
            val x = 5f + index * 5f
            canvas.drawLine(x, 18f - height / 2f, x, 18f + height / 2f, paint)
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        val IDLE_HEIGHTS = floatArrayOf(8f, 15f, 23f, 29f, 23f, 15f, 8f)
    }
}
