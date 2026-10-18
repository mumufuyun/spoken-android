/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.fcitx.fcitx5.android.data.prefs.AppPrefs

object VoiceModePreferences {
    fun orderedScenes(): List<WorkScene> = AppPrefs.getInstance().voice.let {
        VoiceModeOrder.sorted(it.pinnedScenes.getValue(), it.autoOrderScenes.getValue(),
            it.sceneUsage.getValue(), System.currentTimeMillis())
    }

    fun recordCompleted(scene: WorkScene) {
        val pref = AppPrefs.getInstance().voice.sceneUsage
        pref.setValue(VoiceModeOrder.record(pref.getValue(), scene, System.currentTimeMillis()))
    }
}
