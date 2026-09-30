/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceKeyTapActionTest {
    @Test
    fun idleAndErrorStart() {
        assertEquals(VoiceKeyTapAction.START, voiceKeyTapAction(SpokenVoiceState.Idle))
        assertEquals(VoiceKeyTapAction.START, voiceKeyTapAction(SpokenVoiceState.Error("x")))
    }

    @Test
    fun activeSessionStops() {
        assertEquals(VoiceKeyTapAction.STOP, voiceKeyTapAction(SpokenVoiceState.Starting("…")))
        assertEquals(VoiceKeyTapAction.STOP, voiceKeyTapAction(SpokenVoiceState.Recording("hi")))
        assertEquals(VoiceKeyTapAction.STOP, voiceKeyTapAction(SpokenVoiceState.Finishing))
    }

    @Test
    fun aiProcessingSkipsAi() {
        assertEquals(VoiceKeyTapAction.SKIP_AI, voiceKeyTapAction(SpokenVoiceState.AiProcessing))
    }
}
