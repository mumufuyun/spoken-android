/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Xunfei IAT WebSocket protocol helpers, kept free of Android classes so they are
 * unit-testable on the JVM. See https://www.xfyun.cn/doc/asr/voicedictation/API.html
 */
internal object XunfeiIatProtocol {
    const val HOST = "iat-api.xfyun.cn"
    const val PATH = "/v2/iat"

    const val STATUS_FIRST = 0
    const val STATUS_CONTINUE = 1
    const val STATUS_LAST = 2

    private val json = Json { ignoreUnknownKeys = true }

    data class Message(
        val code: Int,
        val message: String?,
        val text: String,
        val isFinal: Boolean
    )

    fun gmtDate(date: Date = Date()): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(date)

    fun authorization(apiKey: String, apiSecret: String, date: String): String {
        val signatureOrigin = "host: $HOST\ndate: $date\nGET $PATH HTTP/1.1"
        val mac = Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        val signature = mac.doFinal(signatureOrigin.toByteArray(Charsets.UTF_8))
            .toByteString().base64()
        val origin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", " +
            "headers=\"host date request-line\", signature=\"$signature\""
        return origin.toByteArray(Charsets.UTF_8).toByteString().base64()
    }

    fun buildSignedUrl(apiKey: String, apiSecret: String, date: Date = Date()): String {
        val gmtDate = gmtDate(date)
        val authorization = authorization(apiKey, apiSecret, gmtDate)
        return "wss://$HOST$PATH?" +
            "authorization=${authorization.urlEncode()}" +
            "&date=${gmtDate.urlEncode()}" +
            "&host=$HOST"
    }

    fun buildAudioFrame(
        appId: String,
        audio: ByteArray,
        status: Int,
        language: String = "zh_cn"
    ): String = buildJsonObject {
        if (status == STATUS_FIRST) {
            put("common", buildJsonObject { put("app_id", appId) })
            put("business", buildJsonObject {
                put("language", language)
                put("domain", "iat")
                put("accent", "mandarin")
                // No dwa=wpgs: dynamic correction re-sends segments with sn/pgs/rg replacement
                // semantics that the parser does not implement; without it each segment is
                // delivered exactly once and plain append is correct.
                put("vad_eos", 8000)
                put("nunum", false)
            })
        }
        put("data", buildJsonObject {
            put("status", status)
            put("format", "audio/L16;rate=16000")
            put("encoding", "raw")
            put("audio", audio.toByteString().base64())
        })
    }.toString()

    /** Returns null when [raw] is not a well-formed IAT message. */
    fun parseMessage(raw: String): Message? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        val message = root["message"]?.jsonPrimitive?.contentOrNull
        if (code != 0) return Message(code, message, "", isFinal = false)
        val data = root["data"]?.jsonObject
        val text = data?.get("result")?.jsonObject?.get("ws")?.jsonArray
            ?.let(::extractWords).orEmpty()
        Message(
            code = 0,
            message = message,
            text = text,
            isFinal = data?.get("status")?.jsonPrimitive?.intOrNull == STATUS_LAST
        )
    }.getOrNull()

    private fun extractWords(ws: kotlinx.serialization.json.JsonArray): String = buildString {
        ws.forEach { item ->
            item.jsonObject["cw"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("w")?.jsonPrimitive?.contentOrNull?.let(::append)
        }
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")
}
