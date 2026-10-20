package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.*
import org.junit.Test

class KeyboardDismissTouchGuardTest {
    private fun guard() = KeyboardDismissTouchGuard(bottomInset = 10f, touchSlop = 8f)
    private fun KeyboardDismissTouchGuard.press(x: Float = 22f, y: Float = 25f, time: Long = 1000L) =
        down(x, y, width = 44f, height = 50f, time = time)

    @Test fun `ordinary center tap dismisses once`() {
        val guard = guard()
        guard.press()
        assertTrue(guard.up(22f, 25f, 1080))
        assertFalse(guard.up(22f, 25f, 1081))
    }

    @Test fun `lower edge near delete never arms even if finger slides to center`() {
        for (y in listOf(40f, 45f, 49f)) {
            val guard = guard()
            guard.press(y = y)
            guard.move(22f, 25f)
            assertFalse(guard.up(22f, 25f, 1080))
        }
    }

    @Test fun `tap just above lower buffer remains usable`() {
        val guard = guard()
        guard.press(y = 39f)
        assertTrue(guard.up(22f, 39f, 1080))
    }

    @Test fun `release in buffer cancels even without a move event`() {
        val guard = guard()
        guard.press(y = 36f)
        assertFalse(guard.up(22f, 41f, 1080))
    }

    @Test fun `release outside button cancels even within system slop`() {
        val guard = guard()
        guard.press(x = 2f)
        assertFalse(guard.up(-1f, 25f, 1080))
    }

    @Test fun `sliding inside button is not a tap and returning cannot restore it`() {
        val guard = guard()
        guard.press()
        guard.move(35f, 25f)
        guard.move(22f, 25f)
        assertFalse(guard.up(22f, 25f, 1080))
    }

    @Test fun `small natural finger movement still counts as a tap`() {
        val guard = guard()
        guard.press()
        guard.move(25f, 28f)
        assertTrue(guard.up(26f, 29f, 1080))
    }

    @Test fun `press during typing cooldown never fires after waiting it out`() {
        val guard = guard()
        guard.keyboardTouched(1000)
        guard.press(time = 1299)
        assertFalse(guard.up(22f, 25f, 1600))
    }

    @Test fun `fresh tap after cooldown dismisses without extra confirmation`() {
        val guard = guard()
        guard.keyboardTouched(1000)
        guard.press(time = 1300)
        assertTrue(guard.up(22f, 25f, 1380))
    }

    @Test fun `another key touch cancels pending hide and refreshes cooldown`() {
        val guard = guard()
        guard.press()
        guard.keyboardTouched(1050)
        assertFalse(guard.up(22f, 25f, 1100))
        guard.keyboardTouched(1800)
        guard.press(time = 2000)
        assertFalse(guard.up(22f, 25f, 2080))
        guard.press(time = 2100)
        assertTrue(guard.up(22f, 25f, 2180))
    }

    @Test fun `cancelled or missing down cannot dismiss but next gesture can`() {
        val guard = guard()
        assertFalse(guard.up(22f, 25f, 1000))
        guard.press()
        guard.cancel()
        assertFalse(guard.up(22f, 25f, 1080))
        guard.press(time = 1100)
        assertTrue(guard.up(22f, 25f, 1180))
    }

    @Test fun `sampled excursion into buffer cancels even if final position is safe`() {
        val guard = guard()
        guard.press(y = 36f)
        guard.move(22f, 41f)
        guard.move(22f, 36f)
        assertFalse(guard.up(22f, 36f, 1080))
    }
}
