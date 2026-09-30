/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorInt
import splitties.dimensions.dp
import java.util.Random
import kotlin.math.abs

/**
 * Flat Pop waveform (mockup 15): 24 solid round bars (4dp wide, 4dp apart, 2dp corner),
 * vertically centered, heights re-randomized every 80ms with a center-weighted envelope
 * (edges decay to 30% amplitude) and a spring-like overshoot transition. Even bars take the
 * dim color at 55% alpha, as in the mockup.
 */
class SpokenWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context) {

    private val barWidth = dp(BAR_WIDTH_DP).toFloat()
    private val barGap = dp(BAR_GAP_DP).toFloat()
    private val barRadius = dp(BAR_CORNER_DP).toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()
    private val random = Random()
    private val heights = FloatArray(BAR_COUNT)
    private val animators = arrayOfNulls<ValueAnimator>(BAR_COUNT)
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            randomizeTargets()
            postDelayed(this, TICK_MS)
        }
    }

    fun start(@ColorInt color: Int, @ColorInt dimColor: Int) {
        paint.color = color
        dimPaint.color = dimColor
        dimPaint.alpha = DIM_ALPHA
        if (!running) {
            running = true
            post(tick)
        }
    }

    fun stop() {
        running = false
        removeCallbacks(tick)
        animators.forEach { it?.cancel() }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun randomizeTargets() {
        val center = (BAR_COUNT - 1) / 2f
        for (i in 0 until BAR_COUNT) {
            val envelope = 1f - (1f - EDGE_FALLOFF) * (abs(i - center) / center)
            val target = envelope * (MIN_AMPLITUDE + (1f - MIN_AMPLITUDE) * random.nextFloat())
            animateBar(i, target)
        }
    }

    private fun animateBar(index: Int, target: Float) {
        animators[index]?.cancel()
        animators[index] = ValueAnimator.ofFloat(heights[index], target).apply {
            duration = TICK_MS * 2
            interpolator = OvershootInterpolator(SPRING_TENSION)
            addUpdateListener {
                heights[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barGap
        var left = (width - totalWidth) / 2f
        val centerY = height / 2f
        for (i in 0 until BAR_COUNT) {
            val barHeight = (heights[i] * height).coerceAtLeast(barRadius * 2)
            barRect.set(left, centerY - barHeight / 2, left + barWidth, centerY + barHeight / 2)
            canvas.drawRoundRect(barRect, barRadius, barRadius, if (i % 2 == 0) paint else dimPaint)
            left += barWidth + barGap
        }
    }

    companion object {
        const val BAR_COUNT = 24
        private const val BAR_WIDTH_DP = 4
        private const val BAR_GAP_DP = 4
        private const val BAR_CORNER_DP = 2
        private const val TICK_MS = 80L
        private const val EDGE_FALLOFF = 0.3f
        private const val MIN_AMPLITUDE = 0.2f
        private const val SPRING_TENSION = 2f
        private const val DIM_ALPHA = 140
    }
}
