/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executor

class ChinesePersonalLearningTest {
    private val direct = Executor { it.run() }
    private val scope = "PINYIN:SIMPLIFIED"

    @Test
    fun repeatedPairsSurviveReloadAndRankByFrequencyThenRecency() {
        val file = Files.createTempDirectory("chinese-learning").resolve("pairs.json").toFile()
        try {
            var time = 1L
            val dictionary = ChinesePersonalDictionary(file, direct) { time++ }
            dictionary.record(scope, "项目", "评审")
            assertTrue(dictionary.predictions(scope, "项目").isEmpty())
            dictionary.record(scope, "项目", "评审")
            repeat(2) { dictionary.record(scope, "项目", "进度") }
            assertEquals(listOf("进度", "评审"), dictionary.predictions(scope, "项目"))
            dictionary.record(scope, "项目", "评审")
            assertEquals(listOf("评审", "进度"), ChinesePersonalDictionary(file, direct).predictions(scope, "项目"))
            assertTrue(dictionary.predictions("PINYIN:TRADITIONAL", "项目").isEmpty())
        } finally {
            file.parentFile.deleteRecursively()
        }
    }

    @Test
    fun onlyAdjacentAcceptedChineseCommitsEstablishPairs() {
        val dictionary = ChinesePersonalDictionary(null)
        val session = ChinesePersonalLearningSession({ dictionary })
        repeat(2) {
            session.reset()
            session.committed("项目", 0, 2, scope, true)
            session.hidePredictions() // Dismissing suggestions must not prevent learning the next word.
            session.committed("评审", 2, 4, scope, true)
        }
        session.reset()
        session.committed("项目", 0, 2, scope, true)
        assertEquals(listOf("评审"), session.predictions(scope, 2, true, true))
        assertTrue(session.predictions(scope, 2, false, true).isEmpty())
        assertTrue(session.predictions(scope, 2, true, false).isEmpty())
        assertTrue(session.predictions(scope, 0, true, true).isEmpty())
        session.hidePredictions()
        assertTrue(session.predictions(scope, 2, true, true).isEmpty())
    }

    @Test
    fun editorChangesPunctuationRestrictedFieldsAndCursorMovesBreakPairs() {
        val dictionary = ChinesePersonalDictionary(null)
        val session = ChinesePersonalLearningSession({ dictionary })
        repeat(2) {
            session.committed("项目", 0, 2, scope, true)
            session.reset()
            session.committed("评审", 2, 4, scope, true)
            session.committed("项目", 0, 2, scope, true)
            session.committed("，", 2, 3, scope, true)
            session.committed("进度", 3, 5, scope, true)
            session.committed("项目", 0, 2, scope, false)
            session.committed("计划", 2, 4, scope, true)
            session.committed("项目", 0, 2, scope, true)
            session.committed("文档", 10, 12, scope, true)
        }
        assertTrue(dictionary.predictions(scope, "项目").isEmpty())
    }

    @Test
    fun staleContextAndDifferentSchemeDoNotLearnTogether() {
        var time = 1L
        val dictionary = ChinesePersonalDictionary(null)
        val session = ChinesePersonalLearningSession({ dictionary }) { time }
        repeat(2) {
            session.committed("项目", 0, 2, scope, true)
            time += 120001L
            session.committed("评审", 2, 4, scope, true)
            session.committed("项目", 0, 2, scope, true)
            session.committed("进度", 2, 4, "STROKE:SIMPLIFIED", true)
        }
        assertTrue(dictionary.predictions(scope, "项目").isEmpty())
    }

    @Test
    fun corruptFileFallsBackAndUnsupportedContentIsNotLearned() {
        val file = Files.createTempDirectory("chinese-learning").resolve("pairs.json").toFile()
        try {
            file.writeText("not-json")
            val dictionary = ChinesePersonalDictionary(file, direct)
            repeat(2) {
                dictionary.record(scope, "项目", "123456")
                dictionary.record(scope, "项目", "评审，")
                dictionary.record(scope, "项目", "文".repeat(33))
            }
            assertTrue(dictionary.predictions(scope, "项目").isEmpty())
        } finally {
            file.parentFile.deleteRecursively()
        }
    }

    @Test
    fun customLearningPreservesExactMatchPriorityAndSurvivesReload() {
        val file = Files.createTempDirectory("chinese-phrases").resolve("phrases.json").toFile()
        fun dictionary() = ChineseT9CustomPhraseDictionary(T9DictionaryPersistence(
            file, emptyList(),
            decode = { Json.decodeFromString<List<ChineseT9CustomPhrase>>(it.joinToString("\n")) },
            encode = { Json.encodeToString(it) }, executor = direct
        ))
        try {
            // Existing files have no learning fields.
            file.writeText("""[{"text":"你好","pinyin":"ni'hao"},{"text":"你号","pinyin":"ni'hao"},{"text":"你好呀","pinyin":"ni'hao'ya"}]""")
            val phrases = dictionary()
            val first = phrases.candidatesForDigits("64426", 10).first().text
            val other = if (first == "你好") "你号" else "你好"
            phrases.recordSelection(other, "ni hao", allowed = false)
            assertEquals(first, phrases.candidatesForDigits("64426", 10).first().text)
            phrases.recordSelection(other, "ni hao", allowed = true)
            repeat(3) { phrases.recordSelection("你好呀", "ni hao ya", allowed = true) }
            assertEquals(other, dictionary().candidatesForDigits("64426", 1).single().text)
            // Editing another phrase must retain learned weights; deletion must remove them.
            phrases.replaceEntries(phrases.entries().map { ChineseT9CustomPhrase(it.text, it.pinyin) })
            assertEquals(other, phrases.candidatesForDigits("64426", 1).single().text)
            phrases.replaceEntries(phrases.entries().filterNot { it.text == other })
            assertFalse(dictionary().candidatesForDigits("64426", 10).any { it.text == other })
        } finally {
            file.parentFile.deleteRecursively()
        }
    }

    @Test
    fun learnedPredictionKeepsEngineIdentityAndNewPairsHaveExplicitDirectIdentity() {
        val source = ChineseT9CustomCandidateSource()
        val engine = T9PagedCandidates(paged("进度", "评审"), intArrayOf(5, 8))
        val merged = source.mergePredictions(engine, listOf("评审", "计划"))
        assertEquals(listOf("评审", "计划", "进度"), merged.data.candidates.map { it.text })
        assertEquals(8, merged.originalIndices[0])
        assertEquals(5, merged.originalIndices[2])
        assertEquals("计划", source.directCommitText(merged.originalIndices[1], "计划"))
        assertNull(source.directCommitText(merged.originalIndices[1], "其他"))
    }

    @Test
    fun deletingCustomTextAlsoRemovesItsLearnedContinuations() {
        val personal = ChinesePersonalDictionary(null)
        val phrases = ChineseT9CustomPhraseDictionary(T9DictionaryPersistence(
            file = null, defaultValue = emptyList(), decode = { emptyList() }, encode = { "" }
        ), onTextsRemoved = personal::forgetTexts)
        phrases.replaceEntries(listOf(ChineseT9CustomPhrase("科学空间", "ke xue kong jian")))
        repeat(2) {
            personal.record(scope, "打开", "科学空间")
            personal.record(scope, "科学空间", "评测")
        }
        phrases.replaceEntries(emptyList())
        assertTrue(personal.predictions(scope, "打开").isEmpty())
        assertTrue(personal.predictions(scope, "科学空间").isEmpty())
    }

    @Test
    fun customPhraseSelectionPreservesItsReadingAndNeverBecomesAnEngineIndex() {
        val phrases = ChineseT9CustomPhraseDictionary(T9DictionaryPersistence(
            file = null, defaultValue = emptyList(), decode = { emptyList() }, encode = { "" }
        ))
        phrases.replaceEntries(listOf(ChineseT9CustomPhrase("你好", "ni hao")))
        val source = ChineseT9CustomCandidateSource(phraseDictionary = { phrases })
        val sessions = T9CandidateSourceSessions({ 20 }, { null }, { _, _ -> false }, source)
        val snapshot = ChineseT9InputSnapshot(
            rawSequence = "64426", digitSequence = "64426", currentSegment = "64426", fullComposition = "64426",
            model = T9CompositionModel(unresolvedDigits = "64426"), keyCount = 5, filterPrefixes = emptyList(),
            hasPendingPinyinSelection = false, sessionRevision = 1L, scheme = ChineseT9Scheme.PINYIN
        )
        val custom = requireNotNull(sessions.buildChineseCustomCandidates(snapshot, false))
        sessions.updateShownState(T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            custom.data, custom.originalIndices, null)
        val commit = sessions.commitBottomCandidateAt(0) as
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.DirectChineseText
        assertEquals("你好", commit.text)
        assertEquals("ni hao", commit.candidate.comment)
        assertTrue(commit.originalIndex < -1)
        phrases.recordSelection(commit.text, commit.candidate.comment, true)
        assertEquals(1, phrases.entries().single().useCount)
    }

    @Test
    fun directPersonalCandidateCanBeMovedAndSelectedButStaleMappingsCannot() {
        val source = ChineseT9CustomCandidateSource()
        val sessions = T9CandidateSourceSessions({ 20 }, { null }, { _, _ -> false }, source)
        val merged = sessions.mergeChinesePredictions(T9PagedCandidates.Empty, listOf("评审", "进度"))
        sessions.updateShownState(T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_PREDICTION,
            merged.data, merged.originalIndices, null)
        assertNotNull(sessions.moveCurrentBottomCandidate(1))
        val committed = sessions.commitCurrentBottomCandidate()
        assertTrue(committed is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.DirectChineseText)
        assertEquals("进度", (committed as T9CandidateUiSnapshotPipeline.CommitBottomCandidate.DirectChineseText).text)
        source.reset()
        assertNull(sessions.commitCurrentBottomCandidate())
        sessions.updateShownState(T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_PREDICTION,
            paged("旧词"), intArrayOf(-1), null)
        assertNull(sessions.commitCurrentBottomCandidate())
    }

    @Test
    fun learnedOnlyPredictionsRenderAndKeepIdentityAcrossPages() {
        val pipeline = T9CandidateUiSnapshotPipeline({ 8 }, { null })
        val words = listOf("项目评审", "实验设计", "研究进度", "文档整理", "数据分析")
        val input = T9CandidateUiInputSnapshot(
            inputPanel = FcitxEvent.InputPanelEvent.Data(),
            rawPaged = FcitxEvent.PagedCandidateEvent.Data.Empty,
            orientation = FloatingCandidatesOrientation.Horizontal,
            currentlyVisible = false,
            loadingState = ChineseT9CandidateLoadingState(),
            widthBudget = null,
            chineseT9Active = true,
            smartEnglishActive = false,
            chineseSnapshot = ChineseT9InputSnapshot(
                rawSequence = "", digitSequence = "", currentSegment = "", fullComposition = "",
                model = T9CompositionModel(), keyCount = 0, filterPrefixes = emptyList(),
                hasPendingPinyinSelection = false, sessionRevision = 1L, scheme = ChineseT9Scheme.PINYIN
            ),
            smartEnglishSnapshot = null,
            learnedChinesePredictions = words,
            chinesePredictionPhase = ChinesePredictionCandidateSession.Phase.VISIBLE,
            pendingPunctuationRawPaged = null,
            currentFocus = T9CandidateFocus.BOTTOM
        )
        val first = requireNotNull(pipeline.build(input))
        assertEquals(T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_PREDICTION, first.interactionState.shownSource)
        val firstText = first.shownState.paged.candidates.first().text
        assertEquals("项目评审", firstText)
        assertEquals(firstText, (pipeline.commitBottomCandidateAt(0) as
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.DirectChineseText).text)
        assertEquals(T9CandidateUiSnapshotPipeline.PageOffset.Refresh, pipeline.offsetCurrentPage(1))
        val second = requireNotNull(pipeline.build(input))
        val secondText = second.shownState.paged.candidates.first().text
        assertNotEquals(firstText, secondText)
        assertEquals(secondText, (pipeline.commitBottomCandidateAt(0) as
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.DirectChineseText).text)
        pipeline.build(input.copy(learnedChinesePredictions = emptyList(),
            chinesePredictionPhase = ChinesePredictionCandidateSession.Phase.OFF))
        assertFalse(pipeline.hasCurrentBottomCandidateRow)
        assertNull(pipeline.commitCurrentBottomCandidate())
    }

    private fun paged(vararg text: String) = FcitxEvent.PagedCandidateEvent.Data(
        candidates = text.map { FcitxEvent.Candidate("", it, "") }.toTypedArray(),
        cursorIndex = 0,
        layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
        hasPrev = false,
        hasNext = false
    )
}
