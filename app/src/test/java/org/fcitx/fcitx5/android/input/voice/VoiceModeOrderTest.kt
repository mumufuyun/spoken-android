package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceModeOrderTest {
    private val now = VoiceModeOrder.DAY_MS * 100
    private fun ordered(history: String = "", pins: String = "", automatic: Boolean = true) =
        VoiceModeOrder.sorted(pins, automatic, history, now)
    private fun uses(scene: WorkScene, count: Int, history: String = "", time: Long = now): String =
        (1..count).fold(history) { value, _ -> VoiceModeOrder.record(value, scene, time) }

    @Test fun `new users keep every mode in canonical order`() {
        assertEquals(WorkScene.entries.toList(), ordered())
    }

    @Test fun `three completed uses qualify and ties keep canonical order`() {
        val twice = uses(WorkScene.PROMPT, 2)
        assertEquals(WorkScene.entries.toList(), ordered(twice))
        val three = uses(WorkScene.PROMPT, 1, twice)
        assertEquals(WorkScene.PROMPT, ordered(three).first())
        assertEquals(listOf(WorkScene.WORK, WorkScene.PROMPT),
            ordered(uses(WorkScene.WORK, 3, three)).take(2))
    }

    @Test fun `pin order wins over frequency without dropping other modes`() {
        val result = ordered(uses(WorkScene.PROMPT, 10), "CHAT,SMOOTH,CHAT,UNKNOWN")
        assertEquals(listOf(WorkScene.CHAT, WorkScene.SMOOTH, WorkScene.PROMPT), result.take(3))
        assertEquals(WorkScene.entries.toSet(), result.toSet())
        assertEquals(8, result.size)
    }

    @Test fun `turning off automatic order preserves pins and canonical remainder`() {
        assertEquals(listOf(WorkScene.SHARE) + WorkScene.entries.filterNot { it == WorkScene.SHARE },
            ordered(uses(WorkScene.PROMPT, 5), "SHARE", automatic = false))
    }

    @Test fun `window includes today and previous twenty nine days only`() {
        val expired = uses(WorkScene.PROMPT, 5, time = now - 30 * VoiceModeOrder.DAY_MS)
        val boundary = uses(WorkScene.WORK, 3, expired, now - 29 * VoiceModeOrder.DAY_MS)
        assertEquals(WorkScene.WORK, ordered(boundary).first())
        assertEquals(WorkScene.PROMPT, ordered(boundary).last())
    }

    @Test fun `malformed future duplicate and excessive records cannot inflate ranking`() {
        val history = "PROMPT,100,2;PROMPT,100,2;WORK,101,99;FORMAL,100,-1;CHAT,100,1000;SHARE,broken,9;X,100,9"
        assertEquals(WorkScene.entries.toList(), ordered(history))
        assertEquals(WorkScene.PROMPT, ordered(uses(WorkScene.PROMPT, 1, history)).first())
    }

    @Test fun `record roundtrip retains daily totals and prunes expired history`() {
        val history = uses(WorkScene.PROMPT, 2, time = now - VoiceModeOrder.DAY_MS)
        val result = uses(WorkScene.PROMPT, 1, history + ";CHAT,1,500")
        assertEquals("PROMPT,99,2;PROMPT,100,1", result)
        assertEquals(WorkScene.PROMPT, ordered(result).first())
    }

    @Test fun `daily counts and record size remain bounded`() {
        var history = ""
        for (day in 1..100) for (scene in WorkScene.entries) {
            history = VoiceModeOrder.record(history, scene, VoiceModeOrder.DAY_MS * day)
        }
        assertEquals(240, history.split(';').size)
        assertTrue(history.length < 16_384)
        assertEquals("PROMPT,100,999", uses(WorkScene.PROMPT, 1002))
    }
}
