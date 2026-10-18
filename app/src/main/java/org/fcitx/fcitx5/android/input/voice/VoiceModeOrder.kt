/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/** Bounded, content-free daily counts. Ties retain the canonical scene order. */
object VoiceModeOrder {
    const val DAY_MS = 86_400_000L
    private const val WINDOW_DAYS = 30
    private const val MIN_USES = 3
    private const val MAX_DAILY_USES = 999
    private const val MAX_RECORDS = WINDOW_DAYS * 8
    private data class Usage(val scene: WorkScene, val day: Long, val count: Int)

    fun pins(raw: String): List<WorkScene> = raw.take(512).split(',')
        .mapNotNull { name -> WorkScene.entries.find { it.name == name } }.distinct()

    private fun usage(raw: String, today: Long): List<Usage> = raw.take(16_384)
        .split(';', limit = MAX_RECORDS + 1).take(MAX_RECORDS).mapNotNull { record ->
            val fields = record.split(',')
            if (fields.size != 3) return@mapNotNull null
            val scene = WorkScene.entries.find { it.name == fields[0] } ?: return@mapNotNull null
            val day = fields[1].toLongOrNull() ?: return@mapNotNull null
            val count = fields[2].toIntOrNull() ?: return@mapNotNull null
            if (day !in (today - WINDOW_DAYS + 1)..today || count !in 1..MAX_DAILY_USES) null
            else Usage(scene, day, count)
        }.distinctBy { it.scene to it.day }

    fun record(raw: String, scene: WorkScene, now: Long): String {
        val today = Math.floorDiv(now, DAY_MS)
        val recent = usage(raw, today)
        val count = recent.find { it.scene == scene && it.day == today }?.count ?: 0
        return (recent.filterNot { it.scene == scene && it.day == today } +
            Usage(scene, today, (count + 1).coerceAtMost(MAX_DAILY_USES)))
            .joinToString(";") { "${it.scene.name},${it.day},${it.count}" }
    }

    fun sorted(pinned: String, automatic: Boolean, history: String, now: Long): List<WorkScene> {
        val first = pins(pinned)
        val remaining = WorkScene.entries.filterNot { it in first }
        if (!automatic) return first + remaining
        val counts = usage(history, Math.floorDiv(now, DAY_MS))
            .groupBy { it.scene }.mapValues { (_, days) -> days.sumOf { it.count } }
        return first + remaining.sortedWith(compareByDescending<WorkScene> {
            (counts[it] ?: 0).takeIf { count -> count >= MIN_USES } ?: 0
        }.thenBy { it.ordinal })
    }
}
