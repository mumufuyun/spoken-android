/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.fcitx.fcitx5.android.input.voice.XunfeiAsrProvider.Companion.FailureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class XunfeiFailurePolicyTest {
    @Test
    fun midSessionLossRetriesTwiceThenFails() {
        assertEquals(FailureAction.RETRY, XunfeiAsrProvider.failureAction(finishing = false, 0))
        assertEquals(FailureAction.RETRY, XunfeiAsrProvider.failureAction(finishing = false, 1))
        assertEquals(FailureAction.FAIL, XunfeiAsrProvider.failureAction(finishing = false, 2))
    }

    @Test
    fun finishingLossCompletesImmediatelyWithoutRetry() {
        // Even with an empty transcript: the user must not wait out the finish timeout.
        assertEquals(FailureAction.COMPLETE, XunfeiAsrProvider.failureAction(finishing = true, 0))
        assertEquals(FailureAction.COMPLETE, XunfeiAsrProvider.failureAction(finishing = true, 2))
    }
}
