/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.voice.VoiceModeOrder
import org.fcitx.fcitx5.android.input.voice.WorkScene
import splitties.dimensions.dp

/** Edits a draft so Cancel never changes the keyboard order. */
object VoiceModeOrderDialog {
    fun show(context: Context) {
        val prefs = AppPrefs.getInstance().voice
        val pinned = VoiceModeOrder.pins(prefs.pinnedScenes.getValue()).toMutableList()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val automatic = SwitchCompat(context).apply {
            isChecked = prefs.autoOrderScenes.getValue()
            contentDescription = context.getString(R.string.voice_modes_automatic)
        }
        body.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                setText(R.string.voice_modes_automatic)
                textSize = 16f
                setPadding(0, dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(automatic)
        })
        body.addView(TextView(context).apply {
            setText(R.string.voice_modes_order_hint)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows)

        fun renderRows() {
            rows.removeAllViews()
            (pinned + WorkScene.entries.filterNot { it in pinned }).forEach { scene ->
                val position = pinned.indexOf(scene)
                val row = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(48)
                }
                row.addView(TextView(context).apply {
                    text = scene.label
                    textSize = 14f
                }, LinearLayout.LayoutParams(0, -2, 1f))
                fun action(label: String, description: String, enabled: Boolean = true, run: () -> Unit) {
                    row.addView(AppCompatButton(context).apply {
                        text = label
                        textSize = 12f
                        contentDescription = description
                        isEnabled = enabled
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(0, 0, 0, 0)
                        setOnClickListener { run(); renderRows() }
                    }, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
                }
                action("↑", context.getString(R.string.voice_mode_move_up, scene.label), position > 0) {
                    pinned.removeAt(position)
                    pinned.add(position - 1, scene)
                }
                action("↓", context.getString(R.string.voice_mode_move_down, scene.label),
                    position >= 0 && position < pinned.lastIndex) {
                    pinned.removeAt(position)
                    pinned.add(position + 1, scene)
                }
                val pinLabel = context.getString(if (position >= 0) R.string.voice_mode_unpin else R.string.voice_mode_pin)
                action(pinLabel, "$pinLabel ${scene.label}") {
                    if (position >= 0) pinned.remove(scene) else pinned.add(scene)
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }
        renderRows()
        AlertDialog.Builder(context)
            .setTitle(R.string.voice_modes_order)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.pinnedScenes.setValue(pinned.joinToString(",") { it.name })
                prefs.autoOrderScenes.setValue(automatic.isChecked)
            }.show()
    }
}
