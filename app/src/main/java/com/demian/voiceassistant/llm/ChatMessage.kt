package com.demian.voiceassistant.llm

/** 模型请求调用的一次「工具」。 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * 一条对话消息。role 取值：system / user / assistant / tool。
 * - assistant 消息可以携带 [toolCalls]（模型决定要调用哪些工具）
 * - tool 消息用 [toolCallId] 对应某个工具调用，content 是执行结果
 */
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

/** 模型一次回复：要么是文字 [content]，要么是要执行的 [toolCalls]。 */
data class LlmResponse(
    val content: String?,
    val toolCalls: List<ToolCall>,
)
