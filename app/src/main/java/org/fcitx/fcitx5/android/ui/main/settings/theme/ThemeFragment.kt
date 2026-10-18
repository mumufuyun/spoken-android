/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import splitties.dimensions.dp
import kotlin.math.roundToInt

class ThemeFragment : Fragment() {

    private lateinit var previewUi: KeyboardPreviewUi

    private lateinit var tabLayout: TabLayout

    private lateinit var viewPager: ViewPager2

    private var tabMediator: TabLayoutMediator? = null

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener {
        lifecycleScope.launch {
            previewUi.setTheme(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = with(requireContext()) {
        previewUi = KeyboardPreviewUi(this, ThemeManager.activeTheme)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        val preview = ScaledKeyboardPreview(this, previewUi)

        tabLayout = TabLayout(this)

        viewPager = ViewPager2(this).apply {
            adapter = object : FragmentStateAdapter(this@ThemeFragment) {
                override fun getItemCount() = 2
                override fun createFragment(position: Int): Fragment = when (position) {
                    0 -> ThemeListFragment()
                    else -> ThemeSettingsFragment()
                }
            }
        }

        tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.theme
                    else -> R.string.configure
                }
            )
        }.also { it.attach() }

        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.spoken_background))
            addView(preview, LinearLayout.LayoutParams(-1, -2))
            addView(tabLayout, LinearLayout.LayoutParams(-1, -2))
            addView(viewPager, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        super.onDestroyView()
    }
}

/** Measure the scaled bounds so the preview cannot leave phantom space or overlap the tabs. */
private class ScaledKeyboardPreview(context: Context, private val preview: KeyboardPreviewUi) :
    ViewGroup(context) {
    private var previewScale = 1f

    init {
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(preview.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val nativeWidth = preview.intrinsicWidth.coerceAtLeast(1)
        val nativeHeight = preview.intrinsicHeight + dp(16)
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val maxPreviewHeight = resources.displayMetrics.heightPixels * 0.30f
        previewScale = minOf(availableWidth.toFloat() / nativeWidth, maxPreviewHeight / nativeHeight, 0.8f)
        preview.root.measure(
            MeasureSpec.makeMeasureSpec(nativeWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(nativeHeight, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, (nativeHeight * previewScale).roundToInt() + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = preview.root
        val x = ((width - child.measuredWidth * previewScale) / 2).roundToInt()
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = previewScale
        child.scaleY = previewScale
        child.layout(x, paddingTop, x + child.measuredWidth, paddingTop + child.measuredHeight)
    }
}
