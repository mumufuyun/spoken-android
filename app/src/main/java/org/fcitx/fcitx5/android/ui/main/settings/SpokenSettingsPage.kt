/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.fcitx.fcitx5.android.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import splitties.dimensions.dp

/** Shared Flat Pop surfaces for the product's settings entry pages. */
abstract class SpokenSettingsPage : Fragment() {
    protected lateinit var content: LinearLayout
    protected val ink get() = ContextCompat.getColor(requireContext(), R.color.spoken_on_surface)
    protected val accent get() = ContextCompat.getColor(requireContext(), R.color.spoken_accent)
    protected val surface get() = ContextCompat.getColor(requireContext(), R.color.spoken_surface)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        return ScrollView(requireContext()).apply {
            isFillViewport = true
            setBackgroundColor(ContextCompat.getColor(context, R.color.spoken_background))
            addView(content)
            clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildPage()
    }

    protected abstract fun buildPage()

    protected fun rounded(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = requireContext().dp(radius).toFloat()
    }

    protected fun label(value: String, size: Float = 14f, strong: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(ink)
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    protected fun heading(title: String, subtitle: String) {
        content.addView(label(title, 30f, true))
        content.addView(label(subtitle, 14f).apply {
            alpha = .7f
            setPadding(0, dp(6), 0, dp(24))
        })
    }

    protected fun section(title: String) {
        content.addView(label(title, 13f).apply {
            alpha = .7f
            setPadding(dp(4), dp(18), 0, dp(10))
        })
    }

    protected fun card(): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface)
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    protected fun LinearLayout.row(
        title: String, subtitle: String, @DrawableRes icon: Int, action: () -> Unit
    ) {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(12), dp(16))
            minimumHeight = dp(80)
            isFocusable = true
            isClickable = true
            val value = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            foreground = ContextCompat.getDrawable(context, value.resourceId)
            setOnClickListener { action() }
        }
        row.addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, true))
            addView(label(subtitle, 13f).apply { alpha = .7f; setPadding(0, dp(4), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 24f).apply { alpha = .5f; setPadding(dp(8), 0, 0, 0) })
        addView(row)
    }

    protected fun inputPlayground() {
        section(getString(R.string.spoken_try_keyboard))
        card().apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(EditText(context).apply {
                id = R.id.spoken_settings_test_input
                hint = getString(R.string.spoken_try_hint)
                textSize = 16f
                minLines = 2
                maxLines = 4
                setTextColor(ink)
                setHintTextColor(ColorStateList.valueOf(ink).withAlpha(150))
                backgroundTintList = ColorStateList.valueOf(accent)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }, LinearLayout.LayoutParams(-1, -2))
            addView(label(getString(R.string.spoken_choose_keyboard), 13f).apply {
                setTextColor(accent)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(48)
                isFocusable = true
                setOnClickListener {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                }
            })
        }
    }
}
