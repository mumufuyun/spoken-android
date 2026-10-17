/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CancellationException
import timber.log.Timber

enum class AiFallbackReason { REQUEST_FAILED, EMPTY_RESPONSE, UNSAFE_OUTPUT }

data class AiProcessingResult(val text: String, val fallback: AiFallbackReason? = null)

class AiProcessor(
    private val enabled: Boolean,
    private val client: AiCompletionClient
) {
    /** Failure retains the transcript; cancellation abandons the request without delivery. */
    suspend fun process(text: String, scene: WorkScene, language: OutputLanguage): AiProcessingResult {
        if (!enabled || text.isBlank() ||
            (scene == WorkScene.DIRECT && language == OutputLanguage.ORIGINAL)) {
            return AiProcessingResult(text)
        }
        val result = try {
            client.complete(Prompts.system(scene, language), text, scene)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Third-party exception messages can include URLs or response bodies.
            Timber.w("Spoken AI processing failed: %s", error.javaClass.simpleName)
            return AiProcessingResult(text, AiFallbackReason.REQUEST_FAILED)
        }
        if (result.isBlank()) return AiProcessingResult(text, AiFallbackReason.EMPTY_RESPONSE)
        return AiOutputGuard.clean(result)?.let { AiProcessingResult(it) }
            ?: AiProcessingResult(text, AiFallbackReason.UNSAFE_OUTPUT)
    }
}
