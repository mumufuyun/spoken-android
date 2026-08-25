/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            VoicePermissionResult.publish(true)
            finish()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            VoicePermissionResult.publish(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE = 9204

        fun launch(context: Context) {
            context.startActivity(
                Intent(context, VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

object VoicePermissionResult {
    @Volatile
    private var listener: ((Boolean) -> Unit)? = null

    fun listenOnce(callback: (Boolean) -> Unit) {
        listener = callback
    }

    fun clear(callback: ((Boolean) -> Unit)? = null) {
        if (callback == null || listener === callback) listener = null
    }

    internal fun publish(granted: Boolean) {
        val callback = listener
        listener = null
        callback?.invoke(granted)
    }
}
