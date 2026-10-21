/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executor

class ChinesePersonalPhrasesTest {
    private val scope = "PINYIN:SIMPLIFIED"
    private val phrases = ChinesePersonalPhraseDictionary(null)
    private val pairs = ChinesePersonalDictionary(null)
    private val session = ChinesePersonalLearningSession({ pairs }, { phrases })

    private fun commit(text: String, reading: String, start: Int) {
        session.stageSelection(text, reading)
        session.committed(text, start, start + text.length, scope, true)
    }

    @Test fun splitSelectionsTeachFullPinyinAndInitialsOnlyAfterConfirmation() {
        repeat(2) {
            session.reset()
            commit("项目", "xiang mu", 0)
            commit("评审", "ping shen", 2)
            session.confirmBoundary(4, scope, true)
        }
        assertEquals("项目评审", phrases.candidates(scope, "942646874647436").single().text)
        assertEquals("项目评审", phrases.candidates(scope, "9677").single().text)
        assertTrue(phrases.candidates(scope, "9").isEmpty())
        assertTrue(phrases.candidates("PINYIN:TRADITIONAL", "9677").isEmpty())
    }

    @Test fun immediateEditAndRejectedSelectionCannotTeachACompound() {
        repeat(3) {
            commit("项目", "xiang mu", 0)
            commit("评审", "ping shen", 2)
            session.reset() // Backspace, caret move, field change, or cancellation.
        }
        assertTrue(phrases.candidates(scope, "9677").isEmpty())
        assertTrue(pairs.predictions(scope, "项目").isEmpty())
        val token = session.stageSelection("评审", "ping shen")
        session.rejectSelection(token)
        session.committed("评审", 0, 2, scope, true)
        session.confirmBoundary(2, scope, true)
        assertTrue(phrases.candidates(scope, "77").isEmpty())
    }

    @Test fun contextSurvivesDifferentWordBoundaries() {
        repeat(2) {
            commit("项目", "xiang mu", 0)
            commit("评审", "ping shen", 2)
            commit("安排", "an pai", 4)
            session.confirmBoundary(6, scope, true)
        }
        commit("项目评审", "xiang mu ping shen", 0)
        assertEquals("安排", session.predictions(scope, 4, true, true).first())
        session.hidePredictions()
        assertTrue(session.predictions(scope, 4, true, true).isEmpty())
        assertEquals("安排", session.candidates(scope, 4, "26724", true).context.first())
        assertTrue(session.candidates(scope, 4, "26724", false).phrases.isEmpty())
        assertTrue(session.candidates(scope, 9, "26724", true).context.isEmpty())
    }

    @Test fun invalidReadingsAndRestrictedCommitsCannotLearn() {
        assertNull(ChinesePersonalPhraseDictionary.reading("重庆", "z q"))
        assertNull(ChinesePersonalPhraseDictionary.reading("实施", "sh sh"))
        assertNull(ChinesePersonalPhraseDictionary.reading("重庆", "ch qing"))
        assertTrue(ChinesePersonalPhraseDictionary.matchRank("sh sh", "7474") > 0)
        assertNull(ChinesePersonalPhraseDictionary.reading("重庆", "chong"))
        assertNull(ChinesePersonalPhraseDictionary.reading("重庆", "［chong qing］"))
        assertEquals("chong'qing", ChinesePersonalPhraseDictionary.reading("重庆", "chong qing"))
        repeat(2) {
            session.stageSelection("重庆", "chong qing")
            session.committed("重庆", 0, 2, scope, false)
            session.confirmBoundary(2, scope, true)
        }
        assertTrue(phrases.candidates(scope, "27").isEmpty())
    }

    @Test fun confirmedPhrasesSurviveReloadAndCanBeForgotten() {
        val file = Files.createTempDirectory("learned-phrases").resolve("phrases.json").toFile()
        try {
            val dictionary = ChinesePersonalPhraseDictionary(file, Executor { it.run() })
            repeat(2) { dictionary.record(scope, "项目评审", "xiang mu ping shen") }
            val reloaded = ChinesePersonalPhraseDictionary(file, Executor { it.run() })
            assertEquals("项目评审", reloaded.candidates(scope, "9677").single().text)
            reloaded.forgetTexts(setOf("项目"))
            assertTrue(ChinesePersonalPhraseDictionary(file).candidates(scope, "9677").isEmpty())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun exactEngineMatchesBeatPrefixesAndContextKeepsEngineIdentity() {
        val manual = ChineseT9CustomPhraseDictionary(T9DictionaryPersistence(
            null, emptyList(), decode = { emptyList() }, encode = { "" }))
        manual.replaceEntries(listOf(ChineseT9CustomPhrase("实施方案", "shi shi fang an")))
        val source = ChineseT9CustomCandidateSource(phraseDictionary = { manual })
        val snapshot = ChineseT9InputSnapshot("744744", "744744", "744744", "744744",
            T9CompositionModel(unresolvedDigits = "744744"), 6, emptyList(), false, 1L, ChineseT9Scheme.PINYIN)
        val personal = ChinesePersonalCandidates(
            listOf(FcitxEvent.Candidate("", "实施", "shi shi")), listOf("实施方案", "实施"))
        val custom = source.buildCustomCandidates(snapshot, false, personal)
        val engine = T9PagedCandidates(FcitxEvent.PagedCandidateEvent.Data(
            arrayOf(FcitxEvent.Candidate("", "实时", "shi shi"), FcitxEvent.Candidate("", "实施", "shi shi")),
            0, FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal, false, false), intArrayOf(5, 8))
        val merged = source.mergeWithEngine(engine, custom, false)
        assertEquals(listOf("实施", "实时", "实施方案"), merged.data.candidates.map { it.text })
        assertEquals(8, merged.originalIndices[0])
        assertEquals(5, merged.originalIndices[1])
        assertEquals("实施方案", source.directCommitText(merged.originalIndices[2], "实施方案"))
    }
}
