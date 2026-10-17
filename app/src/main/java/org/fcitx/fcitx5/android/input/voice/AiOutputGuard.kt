/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/** Conservative final-body check. Ambiguous protocol output falls back without guessing a cut. */
internal object AiOutputGuard {
    private val internalMarkers = Regex(
        """(?i)</?\s*(?:think|thinking|analysis|reasoning|reflection|final|answer)(?=[\s>/])|""" +
            """<\|(?:im_start|im_end|start_header_id|end_header_id|channel|message|end|start)\|>|""" +
            """["'](?:reasoning_content|reasoning_details|chain_of_thought)["']\s*:"""
    )
    private val internalHeading = Regex(
        """(?im)^\s*(?:#{1,6}\s*)?(?:思考过程|内部推理|模型推理|chain of thought|internal reasoning)\s*[:：]"""
    )

    fun clean(text: String): String? {
        val result = text.trim()
        val inspection = result.replace(Regex("[\u200B\u200C\u200D\uFEFF]"), "")
            .replace("&lt;", "<").replace("&gt;", ">")
        if (internalMarkers.containsMatchIn(inspection) || internalHeading.containsMatchIn(inspection)) return null
        return result.takeIf { it.isNotEmpty() }
    }
}
