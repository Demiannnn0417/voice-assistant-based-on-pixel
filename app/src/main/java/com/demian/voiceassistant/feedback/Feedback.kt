package com.demian.voiceassistant.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 轻量反馈：提示音 + 震动。
 * 让用户不用看屏幕就知道「被唤醒了 / 可以说了 / 出错了」。
 */
class Feedback(context: Context) {

    private val appContext = context.applicationContext

    private var toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (t: Throwable) {
        Log.w(TAG, "ToneGenerator 初始化失败", t)
        null
    }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** 唤醒时的提示音。 */
    fun beep() {
        runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    /** 麦克风就绪，可以说话了。 */
    fun ready() {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(35, 90))
        }
    }

    /** 出错的震动提示。 */
    fun error() {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
        }
    }

    fun release() {
        runCatching { toneGen?.release() }
        toneGen = null
    }

    companion object {
        private const val TAG = "Feedback"
    }
}
