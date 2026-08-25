/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureVoiceKeyStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasApiKey(): Boolean = prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)

    fun saveApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(cipher.doFinal(key.toByteArray()), Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(): String? = runCatching {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)))
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "spoken_voice_secure"
        const val KEY_CIPHERTEXT = "api_key_ciphertext"
        const val KEY_IV = "api_key_iv"
        const val KEY_ALIAS = "spoken_voice_api_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
