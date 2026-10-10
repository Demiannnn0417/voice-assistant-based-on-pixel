package com.demian.voiceassistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 语音合成（Text-To-Speech）封装。
 *
 * 负责把文字播报出来。Android 的 [TextToSpeech] 初始化是**异步**的：
 * 构造后要等回调返回 SUCCESS 才真正可用。这里用一个待播队列处理
 * 「还没初始化好就调用了 speak()」的情况。
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 尽量用中文；如果该引擎没有中文，则退回系统默认语言
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.getDefault())
                }
                ready = true
                // 把初始化期间攒下的内容按顺序播出来
                while (pending.isNotEmpty()) {
                    speak(pending.removeFirst())
                }
            }
        }
    }

    /** 播报一段文字。会打断上一条（QUEUE_FLUSH）。 */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending.addLast(text)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.currentTimeMillis()}")
    }

    /** 立刻停止当前播报。 */
    fun stop() {
        tts?.stop()
    }

    /** 释放资源，必须调用（一般在页面销毁时）。 */
    fun shutdown() {
        pending.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
