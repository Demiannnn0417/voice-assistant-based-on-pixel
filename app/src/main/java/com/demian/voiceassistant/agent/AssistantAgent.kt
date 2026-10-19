package com.demian.voiceassistant.agent

import com.demian.voiceassistant.actions.DeviceActions
import com.demian.voiceassistant.actions.OfflineActionRouter
import com.demian.voiceassistant.actions.ToolRegistry
import com.demian.voiceassistant.data.ConversationStore
import com.demian.voiceassistant.knowledge.KnowledgeRetriever
import com.demian.voiceassistant.llm.ChatMessage
import com.demian.voiceassistant.llm.DeepSeekClient
import com.demian.voiceassistant.phone.PhoneInfoProvider
import org.json.JSONObject

/**
 * 助手「大脑」。
 *
 * 处理顺序：
 *  1. 本地快速命令（时间/日期/电量，离线即时）；
 *  2. 否则交给大模型，并带上**多轮历史**与**工具调用**能力。
 */
class AssistantAgent(
    private val router: CommandRouter,
    private val llm: DeepSeekClient,
    private val phone: PhoneInfoProvider,
    private val retriever: KnowledgeRetriever,
    private val conversation: ConversationStore,
    private val actions: DeviceActions,
    private val offlineActions: OfflineActionRouter = OfflineActionRouter(),
) {

    suspend fun respond(userText: String): String {
        val text = userText.trim()
        if (text.isEmpty()) return "我没有听清，请再说一次。"

        // 1. 本地问答（时间/日期/电量）
        router.route(text)?.let { return it }

        // 2. 离线动作（手电筒/音量/媒体，无需网络或大模型）
        offlineActions.route(text)?.let { cmd ->
            return actions.execute(cmd.name, cmd.args)
        }

        val knowledge = retriever.search(text)
        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", systemPrompt(knowledge)))
        messages.addAll(conversation.recentMessages(HISTORY_LIMIT))
        messages.add(ChatMessage("user", text))

        // 3. 大模型（带工具调用）
        return try {
            val reply = runToolLoop(messages)
            conversation.add("user", text)
            conversation.add("assistant", reply)
            reply
        } catch (e: Exception) {
            "大模型调用失败：${e.message ?: "未知错误"}"
        }
    }

    /** 反复「模型决定调工具 → 执行 → 把结果喂回模型」，直到模型给出最终文字回答。 */
    private suspend fun runToolLoop(messages: MutableList<ChatMessage>): String {
        val tools = ToolRegistry.tools()
        repeat(MAX_TOOL_ROUNDS) {
            val resp = llm.chat(messages, tools)
            if (resp.toolCalls.isEmpty()) {
                return resp.content?.trim().orEmpty().ifBlank { "抱歉，我没能给出回答。" }
            }
            messages.add(
                ChatMessage(role = "assistant", content = resp.content, toolCalls = resp.toolCalls)
            )
            resp.toolCalls.forEach { call ->
                val args = try {
                    JSONObject(call.arguments.ifBlank { "{}" })
                } catch (_: Exception) {
                    JSONObject()
                }
                val result = actions.execute(call.name, args)
                messages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id))
            }
        }
        // 轮数用尽，再要一次纯文字总结
        return llm.chat(messages, null).content?.trim().orEmpty().ifBlank { "操作已完成。" }
    }

    private fun systemPrompt(knowledge: List<String>): String = buildString {
        append("你是一个运行在安卓手机上的语音助手，用户会用中文和你交流。")
        append("回答要简洁、口语化，通常一两句话，方便朗读出来。")
        append("当用户要求执行操作（设闹钟、打开应用、控制媒体、手电筒、调音量、打电话、记便签等）时，请调用对应的工具函数。")
        append("涉及时间时，参考下面的当前时间来计算。")
        append("下面是手机的实时信息：")
        append("【${phone.time()}】【${phone.date()}】【${phone.battery()}】")
        if (knowledge.isNotEmpty()) {
            append("\n\n下面是来自本地知识库的参考资料，请优先依据它回答；")
            append("如果资料里没有相关信息，就如实说不知道，不要编造：\n")
            knowledge.forEachIndexed { index, chunk ->
                append("资料${index + 1}：").append(chunk).append('\n')
            }
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 12
        private const val MAX_TOOL_ROUNDS = 4
    }
}
