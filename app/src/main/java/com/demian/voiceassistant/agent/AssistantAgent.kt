package com.demian.voiceassistant.agent

import com.demian.voiceassistant.llm.ChatMessage
import com.demian.voiceassistant.llm.DeepSeekClient
import com.demian.voiceassistant.phone.PhoneInfoProvider

/**
 * 助手「大脑」：决定一条用户命令该怎么回答。
 *
 * 处理顺序：
 *  1. 先走 [CommandRouter]（本地、离线、即时）——时间/日期/电量等；
 *  2. 命中不了再交给 [DeepSeekClient] 大模型。
 */
class AssistantAgent(
    private val router: CommandRouter,
    private val llm: DeepSeekClient,
    private val phone: PhoneInfoProvider
) {

    suspend fun respond(userText: String): String {
        val text = userText.trim()
        if (text.isEmpty()) return "我没有听清，请再说一次。"

        // 1. 本地命令
        router.route(text)?.let { return it }

        // 2. 大模型
        return try {
            llm.chat(
                listOf(
                    ChatMessage("system", systemPrompt()),
                    ChatMessage("user", text)
                )
            )
        } catch (e: Exception) {
            "大模型调用失败：${e.message ?: "未知错误"}"
        }
    }

    private fun systemPrompt(): String = buildString {
        append("你是一个运行在安卓手机上的语音助手，用户会用中文和你交流。")
        append("回答要简洁、口语化，通常一两句话，方便朗读出来。")
        append("下面是手机的实时信息，回答相关问题时可直接使用：")
        append("【${phone.time()}】【${phone.date()}】【${phone.battery()}】")
    }
}
