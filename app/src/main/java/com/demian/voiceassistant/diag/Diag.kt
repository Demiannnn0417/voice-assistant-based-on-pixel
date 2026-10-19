package com.demian.voiceassistant.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 轻量应用内日志（环形缓冲，最多保留 200 条）。
 * 真机出问题时，直接在「诊断日志」页就能看到关键事件，不必连电脑看 Logcat。
 */
object Diag {

    private const val MAX_LINES = 200
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${LocalTime.now().format(timeFormatter)} [$tag] $message"
        _logs.value = (_logs.value + line).takeLast(MAX_LINES)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
