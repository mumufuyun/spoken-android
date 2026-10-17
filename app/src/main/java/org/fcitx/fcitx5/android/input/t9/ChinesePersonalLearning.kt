/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.concurrent.Executor

@Serializable
internal data class ChineseLearnedPair(
    val scope: String,
    val previous: String,
    val next: String,
    val count: Int = 1,
    val lastUsed: Long = 0
)

/** Supplements the shipped prediction data; ordinary Pinyin word learning stays in Rime. */
class ChinesePersonalDictionary internal constructor(
    file: File?,
    executor: Executor = T9DictionaryPersistence.DefaultExecutor,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val persistence = T9DictionaryPersistence(
        file = file,
        defaultValue = emptyList<ChineseLearnedPair>(),
        decode = { Json.decodeFromString<List<ChineseLearnedPair>>(it.joinToString("\n")) },
        encode = { Json.encodeToString(it) },
        executor = executor
    )
    private var pairs = persistence.snapshot().filter {
        isChineseLearningText(it.previous) && isChineseLearningText(it.next) && it.count > 0
    }.distinctBy { Triple(it.scope, it.previous, it.next) }.takeLast(MaxPairs)
    private var index = buildIndex()

    @Synchronized
    fun record(scope: String, previous: String, next: String) {
        if (!isChineseLearningText(previous) || !isChineseLearningText(next)) return
        val old = index[scope to previous].orEmpty().firstOrNull { it.next == next }
        val entry = ChineseLearnedPair(
            scope, previous, next, ((old?.count ?: 0) + 1).coerceAtMost(10000), now()
        )
        pairs = (pairs.filterNot {
            it.scope == scope && it.previous == previous && it.next == next
        } + entry).takeLast(MaxPairs)
        index = buildIndex()
        persistence.replace(pairs)
    }

    @Synchronized
    fun predictions(scope: String, previous: String, limit: Int = 16): List<String> =
        index[scope to previous].orEmpty()
            // A single accidental commit must not establish a personal suggestion.
            .filter { it.count >= 2 }.take(limit.coerceAtLeast(0)).map { it.next }

    @Synchronized
    fun forgetTexts(texts: Set<String>) {
        val retained = pairs.filterNot { it.previous in texts || it.next in texts }
        if (retained.size == pairs.size) return
        pairs = retained
        index = buildIndex()
        persistence.replace(pairs)
    }

    private fun buildIndex() = pairs.groupBy { it.scope to it.previous }.mapValues { (_, values) ->
        values.sortedWith(compareByDescending<ChineseLearnedPair> { it.count }.thenByDescending { it.lastUsed })
    }

    companion object {
        private const val MaxPairs = 4096
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ChinesePersonalDictionary(File(appContext.filesDir, "t9/chinese-personal-pairs.json"))
        }
    }
}

/** Context is limited to adjacent accepted keyboard commits in the current editor. */
class ChinesePersonalLearningSession(
    private val dictionary: () -> ChinesePersonalDictionary = { ChinesePersonalDictionary.Shared },
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Previous(val text: String, val end: Int, val scope: String, val at: Long)
    private var previous: Previous? = null
    private var predictionVisible = false

    @Synchronized
    fun committed(text: String, start: Int, end: Int, scope: String, allowed: Boolean) {
        if (!allowed || start < 0 || end != start + text.length || !isChineseLearningText(text)) {
            reset()
            return
        }
        previous?.takeIf {
            it.end == start && it.scope == scope && now() - it.at in 0..ContextLifetimeMillis
        }?.let { dictionary().record(scope, it.text, text) }
        previous = Previous(text, end, scope, now())
        predictionVisible = true
    }

    @Synchronized
    fun predictions(scope: String, cursor: Int, enabled: Boolean, allowed: Boolean): List<String> {
        if (!enabled || !allowed || !predictionVisible) return emptyList()
        val context = previous?.takeIf {
            it.scope == scope && it.end == cursor && now() - it.at in 0..ContextLifetimeMillis
        } ?: return emptyList()
        return dictionary().predictions(scope, context.text)
    }

    @Synchronized
    fun hidePredictions() {
        predictionVisible = false
    }

    @Synchronized
    fun reset() {
        previous = null
        predictionVisible = false
    }

    companion object {
        private const val ContextLifetimeMillis = 120000L
    }
}

internal fun isChineseLearningText(text: String): Boolean =
    text.isNotEmpty() && text.codePointCount(0, text.length) <= 32 &&
        text.codePoints().allMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
