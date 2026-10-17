/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AiRequestPolicyTest {
    private fun policy(scene: WorkScene, length: Int, host: String = "dashscope.aliyuncs.com", model: String = "qwen3.8-flash") =
        AiRequestPolicy.forInput("https://$host/compatible-mode/v1", model, scene, "文".repeat(length))

    @Test
    fun qwenMeetingThinkingStartsAt800CharactersOnlyInMinutes() {
        WorkScene.entries.forEach { scene ->
            val short = policy(scene, 799)
            val long = policy(scene, 800)
            assertEquals("false", short.parameters["enable_thinking"].toString())
            assertFalse(short.streaming)
            assertEquals(scene == WorkScene.MINUTES, long.streaming)
            assertEquals((scene == WorkScene.MINUTES).toString(), long.parameters["enable_thinking"].toString())
            assertEquals("0.0", long.parameters["temperature"].toString())
            assertEquals(if (scene == WorkScene.MINUTES) "32768" else "1728", long.parameters["max_tokens"].toString())
        }
    }

    @Test
    fun providerRecognitionRequiresBothTrustedHostAndKnownModel() {
        listOf("dashscope.aliyuncs.com.evil.test", "notmaas.aliyuncs.com", "example.invalid").forEach { host ->
            val result = policy(WorkScene.MINUTES, 900, host)
            assertFalse(result.streaming)
            assertFalse(result.parameters.containsKey("temperature"))
            assertFalse(result.parameters.containsKey("enable_thinking"))
        }
        listOf("dashscope-intl.aliyuncs.com", "test.maas.aliyuncs.com").forEach { host ->
            assertTrue(policy(WorkScene.MINUTES, 800, host).streaming)
        }
        assertFalse(policy(WorkScene.MINUTES, 800, model = "qwen-other").parameters.containsKey("enable_thinking"))
        assertEquals("false", policy(WorkScene.MINUTES, 800, model = "deepseek-v4-flash").parameters["enable_thinking"].toString())
    }

    @Test
    fun outputAllowanceScalesAndStopsAtTheDesktopLimit() {
        assertEquals("256", policy(WorkScene.FORMAL, 1).parameters["max_tokens"].toString())
        assertEquals("16384", policy(WorkScene.FORMAL, 20_000).parameters["max_tokens"].toString())
        val emoji = AiRequestPolicy.forInput("https://dashscope.aliyuncs.com", "qwen3.8-flash", WorkScene.MINUTES, "😀".repeat(799))
        assertFalse(emoji.streaming)
    }

    @Test
    fun knownNonQwenProvidersUseTheirOwnFieldsWithoutInventingTemperature() {
        listOf("api.deepseek.com" to "deepseek-v4-flash", "open.bigmodel.cn" to "glm-5", "api.moonshot.cn" to "kimi-k2.6").forEach { (host, model) ->
            val parameters = policy(WorkScene.WORK, 100, host, model).parameters
            assertEquals("disabled", parameters["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertFalse(parameters.containsKey("temperature"))
        }
        val m3 = policy(WorkScene.WORK, 100, "api.minimax.io", "MiniMax-M3").parameters
        assertEquals("328", m3["max_completion_tokens"].toString())
        assertEquals("true", m3["reasoning_split"].toString())
        assertFalse(m3.containsKey("max_tokens"))
        val m2 = policy(WorkScene.WORK, 100, "api.minimax.io", "MiniMax-M2.5").parameters
        assertEquals("2048", m2["max_tokens"].toString())
        assertFalse(m2.containsKey("thinking"))
    }
}
