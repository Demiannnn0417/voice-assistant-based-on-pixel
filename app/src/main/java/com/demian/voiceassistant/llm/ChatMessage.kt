package com.demian.voiceassistant.llm

/** 一条对话消息。role 取值：system / user / assistant。 */
data class ChatMessage(val role: String, val content: String)
