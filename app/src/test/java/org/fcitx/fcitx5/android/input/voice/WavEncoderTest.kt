/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
    @Test
    fun headerMatchesRiffPcmLayout() {
        val pcm = byteArrayOf(0x01, 0x02, 0x03)
        val wav = WavEncoder.pcmToWav(pcm)
        assertEquals(44 + pcm.size, wav.size)
        // RIFF chunk
        assertEquals("RIFF", wav.sliceArray(0..3).toAscii())
        assertArrayEquals(le4(36 + pcm.size), wav.sliceArray(4..7))
        assertEquals("WAVE", wav.sliceArray(8..11).toAscii())
        // fmt chunk
        assertEquals("fmt ", wav.sliceArray(12..15).toAscii())
        assertArrayEquals(le4(16), wav.sliceArray(16..19))
        assertArrayEquals(le2(1), wav.sliceArray(20..21))
        assertArrayEquals(le2(1), wav.sliceArray(22..23))
        assertArrayEquals(le4(16_000), wav.sliceArray(24..27))
        assertArrayEquals(le4(32_000), wav.sliceArray(28..31))
        assertArrayEquals(le2(2), wav.sliceArray(32..33))
        assertArrayEquals(le2(16), wav.sliceArray(34..35))
        // data chunk
        assertEquals("data", wav.sliceArray(36..39).toAscii())
        assertArrayEquals(le4(pcm.size), wav.sliceArray(40..43))
        assertArrayEquals(pcm, wav.sliceArray(44 until wav.size))
    }

    @Test
    fun emptyPcmStillProducesValidHeader() {
        val wav = WavEncoder.pcmToWav(ByteArray(0))
        assertEquals(44, wav.size)
        assertArrayEquals(le4(36), wav.sliceArray(4..7))
        assertArrayEquals(le4(0), wav.sliceArray(40..43))
    }

    private fun le4(value: Int) =
        byteArrayOf(value.toByte(), value.shr(8).toByte(), value.shr(16).toByte(), value.shr(24).toByte())

    private fun le2(value: Int) = byteArrayOf(value.toByte(), value.shr(8).toByte())

    private fun ByteArray.toAscii() = String(this, Charsets.US_ASCII)
}
