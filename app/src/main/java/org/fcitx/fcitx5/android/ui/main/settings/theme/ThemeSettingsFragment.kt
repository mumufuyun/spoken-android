/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.ThemeManager

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    private val followSystemDayNightTheme = ThemeManager.prefs.followSystemDayNightTheme

    private var resumed = false

    private lateinit var switchPreference: SwitchPreference

    // sync SwitchPreference's state when `followSystemDayNightTheme` changed in ThemeListFragment
    private val listener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        if (resumed) return@OnChangeListener
        switchPreference.isChecked = v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followSystemDayNightTheme.registerOnChangeListener(listener)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        switchPreference = findPreference(followSystemDayNightTheme.key)!!
        preferenceScreen.addPreference(Preference(requireContext()).apply {
            key = "full_keyboard_style_scope"
            order = -1
            isIconSpaceReserved = false
            isSelectable = false
            setSummary(R.string.spoken_full_keyboard_style_summary)
        })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onDestroy() {
        followSystemDayNightTheme.unregisterOnChangeListener(listener)
        super.onDestroy()
    }
}
