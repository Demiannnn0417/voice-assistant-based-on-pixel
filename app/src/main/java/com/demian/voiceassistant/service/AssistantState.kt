package com.demian.voiceassistant.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 前台服务与界面之间共享的状态。
 *
 * 服务在后台跑（唤醒 → 识别 → 回答），界面通过订阅这些 StateFlow 实时显示。
 */
object AssistantState {

    /** 当前状态描述，例如「已唤醒」「思考中」。 */
    val status = MutableStateFlow("待机")

    /** 最近一次识别到的内容。 */
    val heard = MutableStateFlow("")

    /** 最近一次的助手回答。 */
    val answer = MutableStateFlow("")

    /** 服务是否正在运行。 */
    val running = MutableStateFlow(false)

    /** 当前麦克风音量（0~1），用于界面波形动画。 */
    val amplitude = MutableStateFlow(0f)
}
