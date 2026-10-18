/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable user files only; bundled engines, model caches and device keys are not backups. */
internal class UserDataFiles(dataDir: File, externalDir: File) {
    private val directories = linkedMapOf(
        "shared_prefs" to File(dataDir, "shared_prefs"),
        "databases" to File(dataDir, "databases"),
        "external" to externalDir,
        "files/t9" to File(dataDir, "files/t9"),
        "files/key-sounds" to File(dataDir, "files/key-sounds"),
        "files/fonts" to File(dataDir, "files/fonts")
    )

    // Android Keystore keys cannot move to another installation. Also skip the ciphertext
    // in older archives on import so it cannot replace this device's working credentials.
    private fun portable(path: String) = path != "shared_prefs/spoken_voice_secure.xml" &&
        path != "shared_prefs/spoken_voice_secure.xml.bak"

    fun writeTo(zip: ZipOutputStream) {
        directories.forEach { (prefix, directory) ->
            if (!directory.isDirectory) return@forEach
            directory.walkTopDown().forEach { file ->
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                val path = if (relative.isEmpty()) prefix else "$prefix/$relative"
                if (portable(path)) {
                    zip.putNextEntry(ZipEntry(if (file.isDirectory) "$path/" else path))
                    if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun restoreFrom(extracted: File) {
        directories.forEach { (prefix, directory) ->
            val source = File(extracted, prefix)
            if (!source.isDirectory) return@forEach
            source.walkTopDown().forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                if (portable("$prefix/$relative")) {
                    val target = File(directory, relative)
                    if (file.isDirectory) target.mkdirs()
                    else file.copyTo(target, overwrite = true)
                }
            }
        }
    }
}
