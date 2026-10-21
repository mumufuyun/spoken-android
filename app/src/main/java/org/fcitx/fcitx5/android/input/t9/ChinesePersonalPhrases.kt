/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.concurrent.Executor

@Serializable
internal data class ChineseLearnedPhrase(
    val scope: String, val text: String, val pinyin: String,
    val count: Int = 1, val lastUsed: Long = 0
)

data class ChinesePersonalCandidates(
    val phrases: List<FcitxEvent.Candidate> = emptyList(),
    val context: List<String> = emptyList()
)

/** Indexed separately from user-managed phrases; only accepted engine readings can teach it. */
class ChinesePersonalPhraseDictionary internal constructor(
    file: File?, executor: Executor = T9DictionaryPersistence.DefaultExecutor,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val persistence = T9DictionaryPersistence(file, emptyList<ChineseLearnedPhrase>(),
        decode = { Json.decodeFromString<List<ChineseLearnedPhrase>>(it.joinToString("\n")) },
        encode = { Json.encodeToString(it) }, executor = executor)
    private var entries = persistence.snapshot().filter {
        reading(it.text, it.pinyin) != null && it.count > 0
    }.distinctBy { Triple(it.scope, it.text, it.pinyin) }.takeLast(4096)
    private var index = buildIndex()

    @Synchronized fun record(scope: String, text: String, pinyin: String) =
        recordAll(scope, listOf(text to pinyin))

    @Synchronized fun recordAll(scope: String, observations: List<Pair<String, String>>) {
        val valid = observations.mapNotNull { (text, pinyin) -> reading(text, pinyin)?.let { text to it } }
            .distinct().toSet()
        if (valid.isEmpty()) return
        val old = entries.filter { it.scope == scope }.associateBy { it.text to it.pinyin }
        val at = now()
        val additions = valid.map { (text, pinyin) ->
            ChineseLearnedPhrase(scope, text, pinyin,
                ((old[text to pinyin]?.count ?: 0) + 1).coerceAtMost(10000), at)
        }
        entries = (entries.filterNot { it.scope == scope && (it.text to it.pinyin) in valid } + additions)
            .takeLast(4096)
        index = buildIndex()
        persistence.replace(entries)
    }

    @Synchronized fun candidates(scope: String, digits: String): List<FcitxEvent.Candidate> =
        index[scope to digits].orEmpty().filter { it.count >= 2 }
            .sortedWith(compareBy<ChineseLearnedPhrase> { matchRank(it.pinyin, digits) }
                .thenByDescending { it.count }.thenByDescending { it.lastUsed })
            .distinctBy { it.text }.take(16)
            .map { FcitxEvent.Candidate("", it.text, it.pinyin.replace('\'', ' ')) }

    @Synchronized fun forgetTexts(texts: Set<String>) {
        val kept = entries.filterNot { entry -> texts.any { it in entry.text } }
        if (kept.size == entries.size) return
        entries = kept
        index = buildIndex()
        persistence.replace(entries)
    }

    private fun buildIndex(): Map<Pair<String, String>, List<ChineseLearnedPhrase>> {
        val result = mutableMapOf<Pair<String, String>, MutableList<ChineseLearnedPhrase>>()
        entries.forEach { entry ->
            val full = digits(entry.pinyin)
            val initials = digits(entry.pinyin.split('\'').joinToString("") { it.take(1) })
            val keys = mutableSetOf(full)
            // Short prefixes should not flood ordinary single-syllable typing with long phrases.
            for (length in 4 until full.length) keys += full.take(length)
            if (initials.length >= 2) keys += initials
            keys.forEach { key -> result.getOrPut(entry.scope to key) { mutableListOf() } += entry }
        }
        return result
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ChinesePersonalPhraseDictionary(File(appContext.filesDir, "t9/chinese-learned-phrases.json"))
        }

        fun reading(text: String, raw: String): String? {
            if (!isChineseLearningText(text) || text.codePointCount(0, text.length) > 16) return null
            val normalized = ChineseT9CustomPhrase.normalizePinyin(raw) ?: return null
            val syllables = normalized.split('\'')
            if (syllables.size != text.codePointCount(0, text.length)) return null
            // Initials and display annotations are not evidence of a word's pronunciation.
            if (syllables.any { !isFullSyllable(it) }) return null
            return normalized
        }

        private fun isFullSyllable(syllable: String): Boolean =
            (syllable.length > 1 || syllable in setOf("a", "o", "e")) &&
                syllable !in setOf("zh", "ch", "sh") &&
                syllable in T9PinyinUtils.t9KeyToPinyin(digits(syllable))

        internal fun digits(pinyin: String): String =
            T9EnglishDictionary.t9DigitsForLetters(pinyin.filter { it in 'a'..'z' }).orEmpty()

        internal fun matchRank(pinyin: String, typed: String): Int {
            if (typed.isEmpty()) return 0
            val normalized = ChineseT9CustomPhrase.normalizePinyin(pinyin) ?: return 3
            val full = digits(normalized)
            val completeReading = normalized.split('\'').all(::isFullSyllable)
            return when {
                completeReading && full == typed -> 0
                completeReading && full.startsWith(typed) -> 1
                digits(normalized.split('\'').joinToString("") { it.take(1) }) == typed -> 2
                else -> 3
            }
        }
    }
}
