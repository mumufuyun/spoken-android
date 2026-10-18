/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserDataFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun File.put(path: String, value: String = path) = resolve(path).apply {
        parentFile.mkdirs()
        writeText(value)
    }

    private fun exportAndExtract(data: File, external: File): File {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { UserDataFiles(data, external).writeTo(it) }
        val extracted = temporary.newFolder()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = extracted.resolve(entry.name)
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return extracted
    }

    @Test fun roundTripIncludesLearningAndCustomAssets() {
        val source = temporary.newFolder()
        val external = temporary.newFolder()
        val paths = listOf(
            "shared_prefs/settings.xml", "databases/clipboard.db",
            "files/t9/chinese-personal-pairs.json", "files/t9/chinese-custom-phrases.json",
            "files/t9/chinese-english-learned.txt", "files/t9/english-learned.txt",
            "files/t9/english-next-learned.tsv", "files/key-sounds/packs/test/key.wav",
            "files/fonts/test.ttf"
        )
        paths.forEach { source.put(it) }
        external.put("data/rime/user.txt")
        val restored = temporary.newFolder()
        val restoredExternal = temporary.newFolder()
        UserDataFiles(restored, restoredExternal).restoreFrom(exportAndExtract(source, external))
        paths.forEach { assertEquals(it, restored.resolve(it).readText()) }
        assertEquals("data/rime/user.txt", restoredExternal.resolve("data/rime/user.txt").readText())
    }

    @Test fun deviceCredentialsAndRuntimeFilesAreExcluded() {
        val source = temporary.newFolder()
        source.put("shared_prefs/spoken_voice_secure.xml", "encrypted")
        source.put("shared_prefs/spoken_voice_secure.xml.bak", "old encrypted")
        source.put("files/usr/engine.so")
        val extracted = exportAndExtract(source, temporary.newFolder())
        assertFalse(extracted.resolve("shared_prefs/spoken_voice_secure.xml").exists())
        assertFalse(extracted.resolve("shared_prefs/spoken_voice_secure.xml.bak").exists())
        assertFalse(extracted.resolve("files/usr").exists())
    }

    @Test fun legacyArchivesPreserveCurrentKeysAndAbsentLearning() {
        val legacy = temporary.newFolder()
        legacy.put("shared_prefs/settings.xml", "imported settings")
        legacy.put("shared_prefs/spoken_voice_secure.xml", "unusable foreign key")
        legacy.put("shared_prefs/spoken_voice_secure.xml.bak", "unusable foreign backup")
        val current = temporary.newFolder()
        current.put("shared_prefs/spoken_voice_secure.xml", "working local key")
        current.put("files/t9/chinese-personal-pairs.json", "current learning")
        UserDataFiles(current, temporary.newFolder()).restoreFrom(legacy)
        assertEquals("imported settings", current.resolve("shared_prefs/settings.xml").readText())
        assertEquals("working local key", current.resolve("shared_prefs/spoken_voice_secure.xml").readText())
        assertFalse(current.resolve("shared_prefs/spoken_voice_secure.xml.bak").exists())
        assertEquals("current learning", current.resolve("files/t9/chinese-personal-pairs.json").readText())
    }

    @Test fun emptyInstallationCanBeExported() {
        val extracted = exportAndExtract(temporary.newFolder(), temporary.newFolder())
        assertEquals(listOf("external"), extracted.listFiles()!!.map { it.name })
    }
}
