/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import org.fcitx.fcitx5.android.input.voice.SpokenAsrProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSettingsPolicyTest {
    @Test
    fun qwenShowsOnlyApiKey() {
        assertEquals(
            setOf(VoiceCredentialField.QWEN_API_KEY),
            VoiceSettingsPolicy.credentialFields(SpokenAsrProviderKind.QWEN)
        )
    }

    @Test
    fun xunfeiShowsAppIdKeyAndSecret() {
        assertEquals(
            setOf(
                VoiceCredentialField.XUNFEI_APP_ID,
                VoiceCredentialField.XUNFEI_API_KEY,
                VoiceCredentialField.XUNFEI_API_SECRET
            ),
            VoiceSettingsPolicy.credentialFields(SpokenAsrProviderKind.XUNFEI)
        )
    }

    @Test
    fun customShowsUrlKeyAndModel() {
        assertEquals(
            setOf(
                VoiceCredentialField.CUSTOM_URL,
                VoiceCredentialField.CUSTOM_API_KEY,
                VoiceCredentialField.CUSTOM_MODEL
            ),
            VoiceSettingsPolicy.credentialFields(SpokenAsrProviderKind.CUSTOM)
        )
    }

    @Test
    fun timeoutAcceptsReasonableIntegers() {
        assertEquals(15, VoiceSettingsPolicy.sanitizeTimeoutSec("15"))
        assertEquals(10, VoiceSettingsPolicy.sanitizeTimeoutSec(" 10 "))
        assertEquals(3, VoiceSettingsPolicy.sanitizeTimeoutSec("3"))
        assertEquals(120, VoiceSettingsPolicy.sanitizeTimeoutSec("120"))
    }

    @Test
    fun timeoutRejectsGarbage() {
        assertNull(VoiceSettingsPolicy.sanitizeTimeoutSec(""))
        assertNull(VoiceSettingsPolicy.sanitizeTimeoutSec("abc"))
        assertNull(VoiceSettingsPolicy.sanitizeTimeoutSec("0"))
        assertNull(VoiceSettingsPolicy.sanitizeTimeoutSec("121"))
        assertNull(VoiceSettingsPolicy.sanitizeTimeoutSec("1.5"))
    }
}
