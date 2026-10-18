/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.common

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.main.modified.MyPreferenceFragment
import org.fcitx.fcitx5.android.utils.applyNavBarInsetsBottomPadding
import splitties.dimensions.dp
import splitties.resources.styledColor

abstract class PaddingPreferenceFragment : MyPreferenceFragment() {
    @CallSuper
    @SuppressLint("RestrictedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        super.onCreateView(inflater, container, savedInstanceState).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.spoken_background))
            setDivider(null)
            listView.setPadding(dp(16), dp(8), dp(16), dp(16))
            listView.clipToPadding = false
            listView.applyNavBarInsetsBottomPadding()
            listView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    out.bottom = dp(4)
                }
            })
            listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val position = listView.getChildAdapterPosition(view)
                    if (position == RecyclerView.NO_POSITION) return
                    val preference = (listView.adapter as? PreferenceGroupAdapter)?.getItem(position)
                    if (preference !is PreferenceCategory) {
                        val surface = GradientDrawable().apply {
                            setColor(ContextCompat.getColor(context, R.color.spoken_surface))
                            cornerRadius = dp(16).toFloat()
                        }
                        val mask = GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(16).toFloat()
                        }
                        view.background = RippleDrawable(
                            ColorStateList.valueOf(context.styledColor(android.R.attr.colorControlHighlight)),
                            surface,
                            mask
                        )
                        view.minimumHeight = dp(72)
                    } else {
                        view.background = null
                    }
                }
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }
}
