/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CustomCandidateSource(
    private val phraseDictionary: () -> ChineseT9CustomPhraseDictionary = {
        ChineseT9CustomPhraseDictionary.Shared
    },
    private val englishDictionaries: () -> EnglishCustomDictionaryCoordinator = {
        EnglishCustomDictionaryCoordinator.Shared
    }
) {
    internal constructor(
        phrases: ChineseT9CustomPhraseDictionary,
        english: EnglishCustomDictionaryCoordinator
    ) : this(
        phraseDictionary = { phrases },
        englishDictionaries = { english }
    )

    private val directCommitTextByIndex = mutableMapOf<Int, String>()
    private var typedDigits = ""
    private var contextual = emptyList<String>()
    private var learnedTexts = emptySet<String>()

    fun buildCustomCandidates(
        snapshot: ChineseT9InputSnapshot,
        englishCandidatesEnabled: Boolean,
        personal: ChinesePersonalCandidates = ChinesePersonalCandidates()
    ): T9PagedCandidates? {
        directCommitTextByIndex.clear()
        typedDigits = if (snapshot.scheme == ChineseT9Scheme.PINYIN) snapshot.digitSequence else ""
        contextual = personal.context
        learnedTexts = personal.phrases.mapTo(HashSet()) { it.text }
        if (snapshot.scheme != ChineseT9Scheme.PINYIN || snapshot.digitSequence.isEmpty()) {
            return null
        }
        val candidates = buildList {
            phraseDictionary().candidatesForDigits(snapshot.digitSequence, CandidateLimit)
                .forEach { entry ->
                    add(
                        FcitxEvent.Candidate(
                            label = "",
                            text = entry.text,
                            comment = entry.pinyin.replace('\'', ' ')
                        )
                    )
                }
            addAll(personal.phrases.take((CandidateLimit - size).coerceAtLeast(0)))
            if (englishCandidatesEnabled) {
                englishDictionaries().candidatesForChinese(
                    snapshot.digitSequence,
                    CandidateLimit - size
                )
                    .forEach { word ->
                        add(FcitxEvent.Candidate(label = "", text = word, comment = ""))
                    }
            }
        }.distinctBy(FcitxEvent.Candidate::text)
        if (candidates.isEmpty()) return null
        val originalIndices = IntArray(candidates.size) { offset ->
            DirectCommitIndexStart + offset
        }
        candidates.forEachIndexed { offset, candidate ->
            directCommitTextByIndex[DirectCommitIndexStart + offset] = candidate.text
        }
        return T9PagedCandidates(
            data = FcitxEvent.PagedCandidateEvent.Data(
                candidates = candidates.toTypedArray(),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = false
            ),
            originalIndices = originalIndices
        )
    }

    fun mergeWithEngine(
        engine: T9PagedCandidates,
        custom: T9PagedCandidates?,
        englishCandidatesEnabled: Boolean
    ): T9PagedCandidates {
        val customIndexed = custom?.indexedCandidates().orEmpty()
        val customTexts = customIndexed.mapTo(HashSet()) { it.value.text }
        val engineIndexed = engine.indexedCandidates().filter { (_, candidate) ->
            candidate.text !in customTexts &&
                (englishCandidatesEnabled || !candidate.text.isPureLatinCandidate())
        }
        if (typedDigits.isEmpty() && customIndexed.isEmpty() && engineIndexed.size == engine.data.candidates.size) {
            return engine
        }
        val engineByText = engine.indexedCandidates().associateBy { it.value.text }
        val merged = (customIndexed.map { candidate ->
            // Keep Rime's identity when a learned phrase is already on its current page.
            if (candidate.value.text in learnedTexts) engineByText[candidate.value.text] ?: candidate else candidate
        } + engineIndexed).sortedWith(
            compareBy<IndexedValue<FcitxEvent.Candidate>> {
                ChinesePersonalPhraseDictionary.matchRank(it.value.comment, typedDigits)
            }.thenBy { contextual.indexOf(it.value.text).takeIf { rank -> rank >= 0 } ?: Int.MAX_VALUE }
        )
        val selectedOriginalIndex = engine.originalIndices.getOrNull(engine.data.cursorIndex)
        val cursor = if (customIndexed.isNotEmpty() || typedDigits.isNotEmpty()) {
            0
        } else {
            merged.indexOfFirst { it.index == selectedOriginalIndex }
                .takeIf { it >= 0 }
                ?: merged.indices.firstOrNull()
                ?: -1
        }
        return T9PagedCandidates(
            data = engine.data.copy(
                candidates = merged.map { it.value }.toTypedArray(),
                cursorIndex = cursor
            ),
            originalIndices = merged.map { it.index }.toIntArray()
        )
    }

    fun directCommitText(originalIndex: Int, candidateText: String): String? =
        directCommitTextByIndex[originalIndex]?.takeIf { it == candidateText }

    fun mergePredictions(engine: T9PagedCandidates, learned: List<String>): T9PagedCandidates {
        if (learned.isEmpty()) return engine
        val engineIndexed = engine.indexedCandidates()
        val byText = engineIndexed.associateBy { it.value.text }
        val personal = learned.distinct().take(CandidateLimit).mapIndexed { offset, text ->
            byText[text] ?: run {
                val index = DirectCommitIndexStart + offset
                directCommitTextByIndex[index] = text
                IndexedValue(index, FcitxEvent.Candidate("", text, ""))
            }
        }
        val personalTexts = personal.mapTo(HashSet()) { it.value.text }
        val merged = personal + engineIndexed.filter { it.value.text !in personalTexts }
        return T9PagedCandidates(
            engine.data.copy(candidates = merged.map { it.value }.toTypedArray(), cursorIndex = 0),
            merged.map { it.index }.toIntArray()
        )
    }

    fun isDirectCommitIndex(originalIndex: Int): Boolean =
        originalIndex in DirectCommitIndexStart..DirectCommitIndexEnd

    fun reset() {
        directCommitTextByIndex.clear()
        typedDigits = ""
        contextual = emptyList()
        learnedTexts = emptySet()
    }

    companion object {
        private const val CandidateLimit = 32
        private const val DirectCommitIndexStart = Int.MIN_VALUE
        private const val DirectCommitIndexEnd = DirectCommitIndexStart + CandidateLimit - 1

        internal fun String.isPureLatinCandidate(): Boolean {
            var hasLetter = false
            forEach { char ->
                when {
                    char in 'a'..'z' || char in 'A'..'Z' -> hasLetter = true
                    char == '\'' || char == '-' -> Unit
                    else -> return false
                }
            }
            return hasLetter
        }
    }
}
