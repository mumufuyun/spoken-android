/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class XunfeiIatProtocolTest {
    private val json = kotlinx.serialization.json.Json

    @Test
    fun firstFrameCarriesCommonAndBusiness() {
        val frame = json.parseToJsonElement(
            XunfeiIatProtocol.buildAudioFrame("app123", byteArrayOf(1, 2, 3), XunfeiIatProtocol.STATUS_FIRST)
        ).jsonObject
        assertEquals("app123", frame["common"]!!.jsonObject["app_id"]!!.jsonPrimitive.content)
        assertEquals("iat", frame["business"]!!.jsonObject["domain"]!!.jsonPrimitive.content)
        val data = frame["data"]!!.jsonObject
        assertEquals(0, data["status"]!!.jsonPrimitive.int)
        assertEquals("audio/L16;rate=16000", data["format"]!!.jsonPrimitive.content)
        assertEquals("AQID", data["audio"]!!.jsonPrimitive.content)
    }

    @Test
    fun continuationFrameOmitsParams() {
        val frame = json.parseToJsonElement(
            XunfeiIatProtocol.buildAudioFrame("app123", byteArrayOf(9), XunfeiIatProtocol.STATUS_CONTINUE)
        ).jsonObject
        assertNull(frame["common"])
        assertNull(frame["business"])
        assertEquals(1, frame["data"]!!.jsonObject["status"]!!.jsonPrimitive.int)
    }

    @Test
    fun endFrameHasStatusTwo() {
        val frame = json.parseToJsonElement(
            XunfeiIatProtocol.buildAudioFrame("app123", ByteArray(0), XunfeiIatProtocol.STATUS_LAST)
        ).jsonObject
        assertEquals(2, frame["data"]!!.jsonObject["status"]!!.jsonPrimitive.int)
        assertEquals("", frame["data"]!!.jsonObject["audio"]!!.jsonPrimitive.content)
    }

    @Test
    fun parsesPartialWords() {
        val raw = """
            {"code":0,"message":"success","sid":"s1","data":{"status":1,"result":{
              "ws":[{"cw":[{"w":"你好"}]},{"cw":[{"w":"世界"}]}]}}}
        """.trimIndent()
        val message = XunfeiIatProtocol.parseMessage(raw)
        assertNotNull(message)
        assertEquals(0, message!!.code)
        assertEquals("你好世界", message.text)
        assertFalse(message.isFinal)
    }

    @Test
    fun parsesFinalMessage() {
        val raw = """
            {"code":0,"message":"success","data":{"status":2,"result":{
              "ws":[{"cw":[{"w":"完成"}]}]}}}
        """.trimIndent()
        val message = XunfeiIatProtocol.parseMessage(raw)
        assertNotNull(message)
        assertTrue(message!!.isFinal)
        assertEquals("完成", message.text)
    }

    @Test
    fun parsesErrorCode() {
        val raw = """{"code":10163,"message":"invalid audio"}"""
        val message = XunfeiIatProtocol.parseMessage(raw)
        assertNotNull(message)
        assertEquals(10163, message!!.code)
        assertEquals("invalid audio", message.message)
        assertFalse(message.isFinal)
    }

    @Test
    fun returnsNullOnMalformedJson() {
        assertNull(XunfeiIatProtocol.parseMessage("not json"))
        assertNull(XunfeiIatProtocol.parseMessage(""))
    }

    @Test
    fun signedUrlIsDeterministicAndCarriesParams() {
        val date = Date(0L)
        val url = XunfeiIatProtocol.buildSignedUrl("key", "secret", date)
        assertTrue(url.startsWith("wss://iat-api.xfyun.cn/v2/iat?"))
        assertTrue(url.contains("host=iat-api.xfyun.cn"))
        assertTrue(url.contains("date=Thu%2C+01+Jan+1970+00%3A00%3A00+GMT"))
        // Same inputs must produce the same signature.
        assertEquals(url, XunfeiIatProtocol.buildSignedUrl("key", "secret", Date(0L)))
        // A different secret changes the authorization parameter.
        assertFalse(url == XunfeiIatProtocol.buildSignedUrl("key", "other", Date(0L)))
    }
}
