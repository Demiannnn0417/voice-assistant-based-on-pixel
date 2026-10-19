package com.demian.voiceassistant.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatMessage → OpenAI 兼容 JSON。抽出来是为了能脱离网络做单元测试。
 */
internal object MessageJson {

    fun toJson(messages: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        messages.forEach { m ->
            val o = JSONObject().apply { put("role", m.role) }
            when {
                m.role == "assistant" && m.toolCalls.isNotEmpty() -> {
                    o.put("content", m.content ?: "")
                    o.put("tool_calls", JSONArray().apply {
                        m.toolCalls.forEach { tc ->
                            put(
                                JSONObject().apply {
                                    put("id", tc.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        JSONObject()
                                            .put("name", tc.name)
                                            .put("arguments", tc.arguments)
                                    )
                                }
                            )
                        }
                    })
                }

                m.role == "tool" -> {
                    o.put("tool_call_id", m.toolCallId ?: "")
                    o.put("content", m.content ?: "")
                }

                else -> o.put("content", m.content ?: "")
            }
            arr.put(o)
        }
        return arr
    }
}
