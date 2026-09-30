/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/**
 * One-time migration from the legacy single-dimension WorkMode preference
 * (key "spoken_default_work_mode", removed) to the scene × language model.
 */
object WorkModeMigration {
    /** Maps a persisted legacy WorkMode name to its (scene, language) equivalent. */
    fun migrate(legacy: String): Pair<WorkScene, OutputLanguage>? = when (legacy) {
        "DIRECT" -> WorkScene.DIRECT to OutputLanguage.ORIGINAL
        "PROMPT" -> WorkScene.PROMPT to OutputLanguage.ORIGINAL
        "MINUTES" -> WorkScene.MINUTES to OutputLanguage.ORIGINAL
        // POLISH rewrote speech into fluent written text; the closest scene is WORK.
        "POLISH" -> WorkScene.WORK to OutputLanguage.ORIGINAL
        // STRUCTURE produced organized formal notes; the closest scene is FORMAL.
        "STRUCTURE" -> WorkScene.FORMAL to OutputLanguage.ORIGINAL
        // TRANSLATE was pure translation: bypass scene styling, output English.
        "TRANSLATE" -> WorkScene.DIRECT to OutputLanguage.ENGLISH
        else -> null
    }
}
