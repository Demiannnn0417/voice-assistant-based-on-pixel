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
 * DeepSeek 对话客户端（OpenAI 兼容接口）。
 *
 * 直接用 [HttpURLConnection] + org.json，避免引入额外依赖。
 * 网络请求必须在 IO 线程执行，所以 [chat] 是 suspend 函数。
 */
class DeepSeekClient(private val settings: SettingsStore) {

    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = settings.deepSeekApiKey
        if (apiKey.isBlank()) {
            throw IOException("未配置 API Key")
        }

        val payload = JSONObject().apply {
            put("model", settings.deepSeekModel)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            })
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

            if (code !in 200..299) {
                throw IOException(parseError(code, body))
            }

            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun parseError(code: Int, body: String): String {
        return try {
            val msg = JSONObject(body).getJSONObject("error").optString("message")
            "HTTP $code：$msg"
        } catch (_: Exception) {
            "HTTP $code：$body"
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    }
}
