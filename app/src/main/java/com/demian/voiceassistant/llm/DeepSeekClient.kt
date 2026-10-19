package com.demian.voiceassistant.llm

import com.demian.voiceassistant.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek 对话客户端（OpenAI 兼容接口，支持函数调用/工具调用）。
 * 网络请求在 IO 线程执行。
 */
class DeepSeekClient(private val settings: SettingsStore) {

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val apiKey = settings.deepSeekApiKey
        if (apiKey.isBlank()) throw IOException("未配置 API Key")

        val payload = JSONObject().apply {
            put("model", settings.deepSeekModel)
            put("stream", false)
            put("messages", MessageJson.toJson(messages))
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }.toString()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IOException(parseError(code, body))

            parseResponse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(body: String): LlmResponse {
        val message = JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")

        val content = if (message.isNull("content")) {
            null
        } else {
            message.optString("content").takeIf { it.isNotBlank() }
        }

        val toolCalls = mutableListOf<ToolCall>()
        message.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val tc = arr.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                toolCalls.add(
                    ToolCall(
                        id = tc.optString("id"),
                        name = fn.optString("name"),
                        arguments = fn.optString("arguments"),
                    )
                )
            }
        }

        return LlmResponse(content = content, toolCalls = toolCalls)
    }

    private fun parseError(code: Int, body: String): String = try {
        val msg = JSONObject(body).getJSONObject("error").optString("message")
        "HTTP $code：$msg"
    } catch (_: Exception) {
        "HTTP $code：$body"
    }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    }
}
