/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.os.Bundle
import android.text.InputType
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.input.voice.SecureVoiceKeyStorage
import org.fcitx.fcitx5.android.input.voice.SpokenAsrProviderKind
import org.fcitx.fcitx5.android.input.voice.VoiceKeyPurpose
import org.fcitx.fcitx5.android.input.voice.WorkMode
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addCategory

internal enum class VoiceCredentialField {
    QWEN_API_KEY,
    XUNFEI_APP_ID,
    XUNFEI_API_KEY,
    XUNFEI_API_SECRET,
    CUSTOM_URL,
    CUSTOM_API_KEY,
    CUSTOM_MODEL
}

internal object VoiceSettingsPolicy {
    fun credentialFields(kind: SpokenAsrProviderKind): Set<VoiceCredentialField> = when (kind) {
        SpokenAsrProviderKind.QWEN -> setOf(VoiceCredentialField.QWEN_API_KEY)
        SpokenAsrProviderKind.XUNFEI -> setOf(
            VoiceCredentialField.XUNFEI_APP_ID,
            VoiceCredentialField.XUNFEI_API_KEY,
            VoiceCredentialField.XUNFEI_API_SECRET
        )

        SpokenAsrProviderKind.CUSTOM -> setOf(
            VoiceCredentialField.CUSTOM_URL,
            VoiceCredentialField.CUSTOM_API_KEY,
            VoiceCredentialField.CUSTOM_MODEL
        )
    }

    fun sanitizeTimeoutSec(raw: String): Int? =
        raw.trim().toIntOrNull()?.takeIf { it in 3..120 }
}

class VoiceSettingsFragment : PaddingPreferenceFragment() {

    private data class SecureKeyUi(
        val purpose: VoiceKeyPurpose,
        val edit: EditTextPreference,
        val clear: Preference
    )

    private val prefs = AppPrefs.getInstance()
    private lateinit var keyStorage: SecureVoiceKeyStorage
    private val secureKeys = mutableMapOf<VoiceCredentialField, SecureKeyUi>()
    private val plainCredentials = mutableMapOf<VoiceCredentialField, Preference>()
    private var engineKind = SpokenAsrProviderKind.QWEN

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        keyStorage = SecureVoiceKeyStorage(ctx)
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addCategory(R.string.voice_recognition) {
                addPreference(buildEnginePreference(ctx))
                addSecureKeyPreference(
                    VoiceCredentialField.QWEN_API_KEY,
                    VoiceKeyPurpose.QWEN,
                    R.string.spoken_voice_api_key,
                    R.string.spoken_voice_api_key_hint
                )
                addAppPrefsTextPreference(
                    R.string.voice_xunfei_app_id,
                    prefs.voice.xunfeiAppId,
                    VoiceCredentialField.XUNFEI_APP_ID
                )
                addSecureKeyPreference(
                    VoiceCredentialField.XUNFEI_API_KEY,
                    VoiceKeyPurpose.XUNFEI_KEY,
                    R.string.voice_xunfei_api_key,
                    R.string.voice_xunfei_api_key
                )
                addSecureKeyPreference(
                    VoiceCredentialField.XUNFEI_API_SECRET,
                    VoiceKeyPurpose.XUNFEI_SECRET,
                    R.string.voice_xunfei_api_secret,
                    R.string.voice_xunfei_api_secret
                )
                addAppPrefsTextPreference(
                    R.string.voice_custom_asr_url,
                    prefs.voice.customAsrUrl,
                    VoiceCredentialField.CUSTOM_URL
                )
                addSecureKeyPreference(
                    VoiceCredentialField.CUSTOM_API_KEY,
                    VoiceKeyPurpose.CUSTOM_KEY,
                    R.string.voice_custom_api_key,
                    R.string.voice_custom_api_key
                )
                addAppPrefsTextPreference(
                    R.string.voice_custom_model,
                    prefs.voice.customAsrModel,
                    VoiceCredentialField.CUSTOM_MODEL
                )
            }
            addCategory(R.string.ai_post_processing) {
                addPreference(buildAiEnabledSwitch(ctx))
                addAppPrefsTextPreference(R.string.ai_base_url, prefs.voice.aiBaseUrl)
                addSecureKeyPreference(null, VoiceKeyPurpose.AI_KEY, R.string.ai_api_key, R.string.ai_api_key)
                addAppPrefsTextPreference(R.string.ai_model, prefs.voice.aiModel)
                addPreference(buildTimeoutPreference(ctx))
                addPreference(buildWorkModePreference(ctx))
            }
        }
        applyEngineVisibility(prefs.voice.asrProvider.getValue())
    }

    private fun buildEnginePreference(ctx: Context): ListPreference {
        val kinds = SpokenAsrProviderKind.entries
        val labels = arrayOf(
            ctx.getString(R.string.voice_engine_qwen),
            ctx.getString(R.string.voice_engine_xunfei),
            ctx.getString(R.string.voice_engine_custom)
        )
        return ListPreference(ctx).apply {
            key = "voice_asr_engine_ui"
            setTitle(R.string.voice_asr_engine)
            entries = labels
            entryValues = kinds.map { it.name }.toTypedArray()
            isPersistent = false
            isIconSpaceReserved = false
            val current = prefs.voice.asrProvider.getValue()
            value = current.name
            summary = labels[kinds.indexOf(current)]
            setOnPreferenceChangeListener { preference, newValue ->
                val kind = SpokenAsrProviderKind.valueOf(newValue as String)
                prefs.voice.asrProvider.setValue(kind)
                preference.summary = labels[kinds.indexOf(kind)]
                applyEngineVisibility(kind)
                false
            }
        }
    }

    private fun buildAiEnabledSwitch(ctx: Context) = SwitchPreferenceCompat(ctx).apply {
        key = "voice_ai_enabled_ui"
        setTitle(R.string.ai_enabled)
        isPersistent = false
        isIconSpaceReserved = false
        isChecked = prefs.voice.aiEnabled.getValue()
        setOnPreferenceChangeListener { _, newValue ->
            prefs.voice.aiEnabled.setValue(newValue == true)
            true
        }
    }

    private fun buildTimeoutPreference(ctx: Context) = EditTextPreference(ctx).apply {
        key = "voice_ai_timeout_ui"
        setTitle(R.string.ai_timeout_sec)
        dialogTitle = ctx.getString(R.string.ai_timeout_sec)
        isPersistent = false
        isIconSpaceReserved = false
        summary = prefs.voice.aiTimeoutSec.getValue().toString()
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.setText(prefs.voice.aiTimeoutSec.getValue().toString())
            editText.setSelection(editText.text.length)
        }
        setOnPreferenceChangeListener { preference, newValue ->
            val seconds = VoiceSettingsPolicy.sanitizeTimeoutSec(newValue?.toString().orEmpty())
            if (seconds != null) {
                prefs.voice.aiTimeoutSec.setValue(seconds)
                preference.summary = seconds.toString()
            }
            false
        }
    }

    private fun buildWorkModePreference(ctx: Context): ListPreference {
        val modes = WorkMode.entries
        return ListPreference(ctx).apply {
            key = "voice_work_mode_ui"
            setTitle(R.string.default_work_mode)
            entries = modes.map { it.label }.toTypedArray()
            entryValues = modes.map { it.name }.toTypedArray()
            isPersistent = false
            isIconSpaceReserved = false
            val current = prefs.voice.defaultMode.getValue()
            value = current.name
            summary = current.description
            setOnPreferenceChangeListener { preference, newValue ->
                val mode = WorkMode.valueOf(newValue as String)
                prefs.voice.defaultMode.setValue(mode)
                preference.summary = mode.description
                false
            }
        }
    }

    private fun PreferenceCategory.addAppPrefsTextPreference(
        @StringRes title: Int,
        pref: ManagedPreference.PString,
        field: VoiceCredentialField? = null
    ) {
        val edit = EditTextPreference(context).apply {
            key = "voice_pref_${pref.key}"
            setTitle(title)
            dialogTitle = context.getString(title)
            isPersistent = false
            isIconSpaceReserved = false
            summary = pref.getValue()
            setOnBindEditTextListener { editText ->
                editText.setText(pref.getValue())
                editText.setSelection(editText.text.length)
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val value = newValue?.toString()?.trim().orEmpty()
                pref.setValue(value)
                preference.summary = value
                false
            }
        }
        addPreference(edit)
        if (field != null) plainCredentials[field] = edit
    }

    private fun PreferenceCategory.addSecureKeyPreference(
        field: VoiceCredentialField?,
        purpose: VoiceKeyPurpose,
        @StringRes title: Int,
        @StringRes hint: Int
    ) {
        val edit = EditTextPreference(context).apply {
            key = "voice_secure_${purpose.name.lowercase()}"
            setTitle(title)
            dialogTitle = context.getString(title)
            dialogMessage = context.getString(hint)
            isPersistent = false
            isIconSpaceReserved = false
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                editText.setText("")
                editText.hint = context.getString(hint)
            }
        }
        val clear = Preference(context).apply {
            key = "voice_secure_clear_${purpose.name.lowercase()}"
            setTitle(R.string.voice_key_clear)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                keyStorage.clear(purpose)
                refreshSecureKey(SecureKeyUi(purpose, edit, this), field)
                true
            }
        }
        val ui = SecureKeyUi(purpose, edit, clear)
        edit.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) keyStorage.save(purpose, value)
            refreshSecureKey(ui, field)
            false
        }
        refreshSecureKey(ui, field)
        addPreference(edit)
        addPreference(clear)
        if (field != null) secureKeys[field] = ui
    }

    private fun refreshSecureKey(ui: SecureKeyUi, field: VoiceCredentialField?) {
        val saved = keyStorage.has(ui.purpose)
        ui.edit.summary = context?.getString(
            if (saved) R.string.voice_key_saved else R.string.voice_key_missing
        )
        val fieldVisible = field == null ||
            field in VoiceSettingsPolicy.credentialFields(engineKind)
        ui.clear.isVisible = saved && fieldVisible
    }

    private fun applyEngineVisibility(kind: SpokenAsrProviderKind) {
        engineKind = kind
        val visible = VoiceSettingsPolicy.credentialFields(kind)
        plainCredentials.forEach { (field, preference) ->
            preference.isVisible = field in visible
        }
        secureKeys.forEach { (field, ui) ->
            ui.edit.isVisible = field in visible
            ui.clear.isVisible = field in visible && keyStorage.has(ui.purpose)
        }
    }
}
