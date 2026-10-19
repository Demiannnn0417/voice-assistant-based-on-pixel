package com.demian.voiceassistant.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessageJsonTest {

    @Test
    fun `普通消息序列化`() {
        val arr = MessageJson.toJson(
            listOf(
                ChatMessage("system", "你是助手"),
                ChatMessage("user", "你好"),
            )
        )
        assertEquals(2, arr.length())
        assertEquals("system", arr.getJSONObject(0).getString("role"))
        assertEquals("你是助手", arr.getJSONObject(0).getString("content"))
        assertEquals("user", arr.getJSONObject(1).getString("role"))
    }

    @Test
    fun `带工具调用的 assistant 消息`() {
        val arr = MessageJson.toJson(
            listOf(
                ChatMessage(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(ToolCall("call_1", "set_alarm", "{\"hour\":7}")),
                )
            )
        )
        val msg = arr.getJSONObject(0)
        assertEquals("assistant", msg.getString("role"))
        val toolCalls = msg.getJSONArray("tool_calls")
        assertEquals(1, toolCalls.length())
        val fn = toolCalls.getJSONObject(0).getJSONObject("function")
        assertEquals("set_alarm", fn.getString("name"))
        assertEquals("{\"hour\":7}", fn.getString("arguments"))
    }

    @Test
    fun `tool 结果消息带上 tool_call_id`() {
        val arr = MessageJson.toJson(
            listOf(ChatMessage(role = "tool", content = "已设置", toolCallId = "call_1"))
        )
        val msg = arr.getJSONObject(0)
        assertEquals("tool", msg.getString("role"))
        assertEquals("call_1", msg.getString("tool_call_id"))
        assertEquals("已设置", msg.getString("content"))
        assertFalse(msg.has("tool_calls"))
    }
}
