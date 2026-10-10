package com.demian.voiceassistant.agent

import com.demian.voiceassistant.phone.PhoneInfoProvider

/**
 * 本地命令路由：先用关键词匹配处理简单的手机信息类命令。
 *
 * 这一步完全离线、不需要任何 API Key。等接入云端大模型后，
 * 命中不了的命令会交给大模型处理（这里返回 null 就代表「我不认识」）。
 */
class CommandRouter(private val phone: PhoneInfoProvider) {

    /** 返回回答文本；无法处理时返回 null。 */
    fun route(command: String): String? {
        val c = command.trim()
        if (c.isEmpty()) return null

        return when {
            // 日期 / 星期（放在时间前面，避免“今天几号”被时间规则截走）
            Regex("几号|几月|日期|星期几|礼拜几|周几").containsMatchIn(c) -> phone.date()

            // 时间
            Regex("几点|时间|时刻").containsMatchIn(c) -> phone.time()

            // 电量
            Regex("电量|电池|充电").containsMatchIn(c) -> phone.battery()

            // 打招呼
            Regex("你好|您好|嗨|在吗|在不在").containsMatchIn(c) -> "你好，我在。"

            else -> null
        }
    }
}
