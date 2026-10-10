package com.demian.voiceassistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 语音合成（Text-To-Speech）封装。
 *
 * Android 的 [TextToSpeech] 初始化是**异步**的：构造后要等回调返回 SUCCESS
 * 才真正可用。这里用待播队列处理「还没初始化好就调用了 speak()」的情况，
 * 并支持在**一条播报结束时**回调（前台服务靠它在播完恢复唤醒）。
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, (() -> Unit)?>>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        callbacks.remove(utteranceId)?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        callbacks.remove(utteranceId)?.invoke()
                    }
                })
                ready = true
                while (pending.isNotEmpty()) {
                    val (text, onDone) = pending.removeFirst()
                    speak(text, onDone)
                }
            }
        }
    }

    /** 播报一段文字。会打断上一条（QUEUE_FLUSH）。onDone 在播报结束时回调。 */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!ready) {
            pending.addLast(text to onDone)
            return
        }
        val id = "utt-${System.currentTimeMillis()}-${text.hashCode()}"
        if (onDone != null) callbacks[id] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        pending.clear()
        callbacks.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
