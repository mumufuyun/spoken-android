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
    fun record(scope: String, previous: String, next: String) = recordContexts(scope, listOf(previous), next)

    @Synchronized
    fun recordContexts(scope: String, contexts: List<String>, next: String) {
        if (!isChineseLearningText(next)) return
        val valid = contexts.filter(::isChineseLearningText).distinct()
        if (valid.isEmpty()) return
        val at = now()
        val additions = valid.map { previous ->
            val old = index[scope to previous].orEmpty().firstOrNull { it.next == next }
            ChineseLearnedPair(scope, previous, next, ((old?.count ?: 0) + 1).coerceAtMost(10000), at)
        }
        pairs = (pairs.filterNot { it.scope == scope && it.next == next && it.previous in valid } + additions)
            .takeLast(MaxPairs)
        index = buildIndex()
        persistence.replace(pairs)
    }

    @Synchronized
    fun predictions(scope: String, previous: String, limit: Int = 16): List<String> =
        index[scope to previous].orEmpty()
            // A single accidental commit must not establish a personal suggestion.
            .filter { it.count >= 2 }.take(limit.coerceAtLeast(0)).map { it.next }

    @Synchronized
    fun contextPredictions(scope: String, context: String): List<String> =
        (minOf(8, context.length) downTo 1).flatMap { length ->
            predictions(scope, context.takeLast(length))
        }.distinct().take(16)

    @Synchronized
    fun forgetTexts(texts: Set<String>) {
        val retained = pairs.filterNot { pair -> texts.any { it in pair.previous || it in pair.next } }
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

/** Learning is confirmed by the next accepted unit/boundary, so immediate edits discard it. */
class ChinesePersonalLearningSession(
    private val dictionary: () -> ChinesePersonalDictionary = { ChinesePersonalDictionary.Shared },
    private val phrases: () -> ChinesePersonalPhraseDictionary = { ChinesePersonalPhraseDictionary.Shared },
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Unit(val text: String, val pinyin: String?)
    private data class Previous(val units: List<Unit>, val end: Int, val scope: String, val at: Long)
    private data class Selection(val text: String, val pinyin: String)
    private var previous: Previous? = null
    private var staged: Selection? = null
    private var pending: (() -> kotlin.Unit)? = null
    private var predictionVisible = false

    @Synchronized fun stageSelection(text: String, pinyin: String): Any {
        val selection = Selection(text, pinyin)
        staged = selection
        return selection
    }

    @Synchronized fun rejectSelection(token: Any?) {
        if (staged === token) staged = null
    }

    @Synchronized
    fun committed(text: String, start: Int, end: Int, scope: String, allowed: Boolean,
                  pinyin: String? = null) {
        val selected = staged?.takeIf { it.text == text }
        staged = null
        if (!allowed || start < 0 || end != start + text.length || !isChineseLearningText(text)) {
            reset()
            return
        }
        val preceding = context(scope, start)
        if (preceding != null) pending?.invoke()
        pending = null
        val reading = (pinyin ?: selected?.pinyin)?.let { ChinesePersonalPhraseDictionary.reading(text, it) }
        val unit = Unit(text, reading)
        val units = (preceding?.units.orEmpty() + unit).takeLast(4)
        val contextText = preceding?.units?.joinToString("") { it.text }.orEmpty()
        // Freeze exactly what was accepted. No editor text is read into the dictionary.
        pending = {
            if (contextText.isNotEmpty()) {
                dictionary().recordContexts(scope,
                    (1..minOf(8, contextText.length)).map(contextText::takeLast), text)
            }
            if (scope.startsWith("PINYIN:")) {
                val observed = (1..units.size).mapNotNull { count ->
                    val part = units.takeLast(count)
                    val phrase = part.joinToString("") { it.text }
                    if (part.all { it.pinyin != null } && phrase.codePointCount(0, phrase.length) in 2..16) {
                        phrase to part.joinToString("'") { it.pinyin!! }
                    } else null
                }
                if (observed.isNotEmpty()) phrases().recordAll(scope, observed)
            }
        }
        previous = Previous(units, end, scope, now())
        predictionVisible = true
    }

    @Synchronized fun confirmBoundary(cursor: Int, scope: String, allowed: Boolean) {
        if (allowed && context(scope, cursor) != null) pending?.invoke()
        reset()
    }

    @Synchronized
    fun predictions(scope: String, cursor: Int, enabled: Boolean, allowed: Boolean): List<String> {
        if (!enabled || !allowed || !predictionVisible) return emptyList()
        return suggestions(scope, cursor)
    }

    @Synchronized fun candidates(scope: String, cursor: Int, digits: String, allowed: Boolean): ChinesePersonalCandidates {
        if (!allowed || !scope.startsWith("PINYIN:")) return ChinesePersonalCandidates()
        return ChinesePersonalCandidates(phrases().candidates(scope, digits), suggestions(scope, cursor))
    }

    private fun suggestions(scope: String, cursor: Int): List<String> {
        val context = context(scope, cursor) ?: return emptyList()
        return dictionary().contextPredictions(scope, context.units.joinToString("") { it.text })
    }

    private fun context(scope: String, cursor: Int): Previous? = previous?.takeIf {
        it.scope == scope && it.end == cursor && now() - it.at in 0..ContextLifetimeMillis
    }

    @Synchronized fun hidePredictions() { predictionVisible = false }

    @Synchronized fun reset() {
        previous = null
        staged = null
        pending = null
        predictionVisible = false
    }

    companion object {
        private const val ContextLifetimeMillis = 120000L
    }
}

internal fun isChineseLearningText(text: String): Boolean =
    text.isNotEmpty() && text.codePointCount(0, text.length) <= 32 &&
        text.codePoints().allMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
