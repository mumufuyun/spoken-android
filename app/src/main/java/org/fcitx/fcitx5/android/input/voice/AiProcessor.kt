/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CancellationException
import timber.log.Timber

class AiProcessor(
    private val enabled: Boolean,
    private val client: AiCompletionClient
) {
    /**
     * Applies [mode] to [text]. DIRECT mode or disabled AI returns the transcript as-is;
     * any AI failure falls back to the transcript so callers never handle exceptions.
     * Cancellation is never swallowed: [CancellationException] propagates so the session
     * lifecycle can abandon in-flight work (e.g. the user tapped to skip AI).
     */
    suspend fun process(text: String, mode: WorkMode): String {
        if (!enabled || mode == WorkMode.DIRECT || text.isBlank()) return text
        val result = runCatching {
            client.complete(Prompts.system(mode), text)
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.w(it, "Spoken AI processing failed, falling back to transcript")
        }.getOrNull()
        return result?.takeIf(String::isNotBlank) ?: text
    }
}
