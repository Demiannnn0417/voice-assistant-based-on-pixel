package com.demian.voiceassistant.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 语音识别（Speech-To-Text）封装。
 *
 * 基于 Android 自带的 [SpeechRecognizer]。它会占用麦克风，识别结束后回调结果。
 * 注意：默认走的是系统的识别服务（Pixel 上通常是 Google 语音服务，需要联网，
 * 或在支持时使用端侧模型）。
 */
class SpeechRecognizerManager(private val context: Context) {

    /** 识别过程回调。所有回调都在主线程。 */
    interface Callback {
        /** 麦克风已就绪，可以开始说。 */
        fun onReady()
        /** 中途的识别结果（边说边出字）。 */
        fun onPartial(text: String)
        /** 最终识别结果。 */
        fun onFinal(text: String)
        /** 出错，error 见 [SpeechRecognizer.ERROR_*]。 */
        fun onError(error: Int)
        /** 一次识别会话结束（无论成功或失败）。 */
        fun onEnd()
        /** 实时音量（0~1），用于波形动画。 */
        fun onRms(level: Float) {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var callback: Callback? = null

    /** 开始一次识别。 */
    fun start(callback: Callback) {
        this.callback = callback

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError(SpeechRecognizer.ERROR_CLIENT)
            callback.onEnd()
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        recognizer?.startListening(buildIntent())
    }

    /** 主动结束录音，等待最终结果。 */
    fun stop() {
        recognizer?.stopListening()
    }

    /** 取消本次识别，不返回结果。 */
    fun cancel() {
        recognizer?.cancel()
        callback?.onEnd()
    }

    /** 彻底释放，页面销毁时调用。 */
    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        callback = null
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 中文识别
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callback?.onReady()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB 大约在 -2 ~ 10 之间，归一化到 0~1
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            callback?.onRms(level)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            callback?.onError(error)
            callback?.onEnd()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            callback?.onFinal(text)
            callback?.onEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) callback?.onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
