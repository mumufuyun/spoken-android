/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.data.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import org.fcitx.fcitx5.android.input.voice.WorkModeMigration

object SettingsPreferenceMigration {
    private const val VersionKey = "product_settings_schema_version"
    private const val CurrentVersion = 2

    // These controls were removed rather than hidden: product behavior now has one owner in code,
    // so retaining their values would make backups and Direct Boot storage imply false support.
    private val version1RemovedKeys = setOf(
        "reset_keyboard_on_focus_change",
        "inline_suggestions",
        "popup_on_key_press",
        "keep_keyboard_letters_uppercase",
        "show_lang_switch_key",
        "lang_switch_key_behavior",
        "use_t9_keyboard_layout",
        "expand_keypress_area",
        "swipe_symbol_behavior",
        "space_long_press_behavior",
        "space_swipe_move_cursor",
        "keyboard_height_percent",
        "keyboard_height_percent_landscape",
        "keyboard_side_padding",
        "keyboard_side_padding_landscape",
        "keyboard_bottom_padding",
        "keyboard_bottom_padding_landscape",
        "horizontal_candidate_style",
        "expanded_candidate_style",
        "expanded_candidate_grid_span_count_portrait",
        "expanded_candidate_grid_span_count_landscape",
        "show_candidates_window",
        "candidates_window_orientation",
        "candidates_window_min_width",
        "candidates_window_padding",
        "candidates_show_pagination_arrows",
        "candidates_window_radius",
        "candidates_horizontal_margin",
        "candidates_bubble_gap",
        "candidates_item_spacing",
        "candidates_small_row_percent",
        "candidates_item_padding_vertical",
        "candidates_item_padding_horizontal",
        "clipboard_suggestion",
        "clipboard_item_timeout",
        "clipboard_return_after_paste"
    )

    fun migrate(preferences: SharedPreferences) {
        val version = preferences.getInt(VersionKey, 0)
        if (version >= CurrentVersion) return
        // Read before entering the editor; Editor has no getters.
        val legacyWorkMode = preferences.getString("spoken_default_work_mode", null)
        preferences.edit {
            if (version < 1) {
                version1RemovedKeys.forEach(::remove)
            }
            if (version < 2) {
                // Legacy single-dimension voice mode -> scene x language pair.
                legacyWorkMode?.let { legacy ->
                    WorkModeMigration.migrate(legacy)?.let { (scene, language) ->
                        putString("spoken_default_scene", scene.name)
                        putString("spoken_output_language", language.name)
                    }
                }
                remove("spoken_default_work_mode")
            }
            putInt(VersionKey, CurrentVersion)
        }
    }
}
