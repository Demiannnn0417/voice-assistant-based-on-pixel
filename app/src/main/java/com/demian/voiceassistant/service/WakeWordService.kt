package com.demian.voiceassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.demian.voiceassistant.MainActivity
import com.demian.voiceassistant.R
import com.demian.voiceassistant.actions.DeviceActions
import com.demian.voiceassistant.agent.AssistantAgent
import com.demian.voiceassistant.agent.CommandRouter
import com.demian.voiceassistant.asr.SpeechRecognizerManager
import com.demian.voiceassistant.data.ConversationStore
import com.demian.voiceassistant.data.NotesStore
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.diag.Diag
import com.demian.voiceassistant.feedback.Feedback
import com.demian.voiceassistant.knowledge.KnowledgeRetriever
import com.demian.voiceassistant.knowledge.KnowledgeStore
import com.demian.voiceassistant.llm.DeepSeekClient
import com.demian.voiceassistant.phone.PhoneInfoProvider
import com.demian.voiceassistant.tts.TtsManager
import com.demian.voiceassistant.wake.PinyinKeywords
import com.demian.voiceassistant.wake.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 常驻前台服务：让 App 在后台/息屏也能被唤醒词唤醒。
 *
 * 稳定性设计：
 *  - ASR / TTS 都有看门狗超时，卡住会自动兜底
 *  - 唤醒引擎异常会自动重启（带退避）
 *  - 记录关键事件到 [Diag]，方便真机排查
 */
class WakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var detector: WakeWordDetector
    private lateinit var asr: SpeechRecognizerManager
    private lateinit var tts: TtsManager
    private lateinit var agent: AssistantAgent
    private lateinit var settings: SettingsStore
    private lateinit var feedback: Feedback

    private var wakeMode = false
    private var listening = false
    private var turns = 0
    private var wakeRestartAttempts = 0

    private var listenWatchdog: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        detector = WakeWordDetector(this)
        asr = SpeechRecognizerManager(this)
        tts = TtsManager(this)
        feedback = Feedback(this)
        val phone = PhoneInfoProvider(this)
        agent = AssistantAgent(
            router = CommandRouter(phone),
            llm = DeepSeekClient(settings),
            phone = phone,
            retriever = KnowledgeRetriever(KnowledgeStore(this)),
            conversation = ConversationStore(this),
            actions = DeviceActions(this, NotesStore(this)),
        )
        AssistantState.running.value = true
        Diag.log("service", "created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Diag.log("service", "onStartCommand action=$action")

        if (action == ACTION_STOP) {
            settings.wakeEnabled = false
            stopEverything()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        when (action) {
            ACTION_LISTEN_NOW -> requestListen() // 保留当前 wakeMode

            ACTION_TEXT_COMMAND -> {
                handleTextCommand(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            }

            ACTION_START_WAKE -> {
                settings.wakeEnabled = true
                wakeMode = true
                startWake()
            }

            // 系统重启服务（intent 为 null）等情况：沿用上次的意图
            else -> {
                wakeMode = true
                startWake()
            }
        }
        return START_STICKY
    }

    // ---- 唤醒 ----

    private fun startWake() {
        if (listening) return
        listening = false
        turns = 0
        val word = settings.wakeWord
        AssistantState.status.value = "常驻监听中（说「$word」）"
        val keywords = PinyinKeywords.keywordsOrDefault(this, word)
        Diag.log("wake", "start listening, word=$word")
        detector.start(
            keywords = keywords,
            onLevel = { level -> AssistantState.amplitude.value = level },
            onDetected = { keyword -> mainHandler.post { onWake(keyword) } },
            onError = { error -> mainHandler.post { onDetectorError(error) } },
        )
    }

    private fun onWake(keyword: String) {
        if (!wakeMode || listening) return
        wakeRestartAttempts = 0
        Diag.log("wake", "detected: $keyword")
        AssistantState.status.value = "已唤醒：$keyword"
        feedback.beep()
        tts.speak("在")
        requestListen()
    }

    /** 唤醒引擎异常：退避后自动重启，避免一次异常就永久失效。 */
    private fun onDetectorError(error: Throwable) {
        Diag.log("wake", "detector error: ${error.message ?: error.javaClass.simpleName}")
        if (!wakeMode || listening) return
        wakeRestartAttempts++
        val delayMs = (2_000L * wakeRestartAttempts).coerceAtMost(30_000L)
        AssistantState.status.value = "唤醒引擎异常，${delayMs / 1000}s 后重试"
        mainHandler.postDelayed({
            if (wakeMode && !listening) {
                Diag.log("wake", "restart attempt #$wakeRestartAttempts")
                startWake()
            }
        }, delayMs)
    }

    // ---- 识别 ----

    private fun requestListen() {
        detector.stop() // 让出麦克风给识别
        if (listening) return
        listening = true
        AssistantState.heard.value = ""
        AssistantState.status.value = if (turns > 0) "继续聆听…" else "正在聆听…"
        Diag.log("asr", "listen start (turn=$turns)")
        asr.start(object : SpeechRecognizerManager.Callback {
            override fun onReady() {
                AssistantState.status.value = "请说话…"
                feedback.ready()
            }

            override fun onPartial(text: String) {
                AssistantState.heard.value = text
            }

            override fun onFinal(text: String) {
                cancelListenWatchdog()
                AssistantState.heard.value = text
                Diag.log("asr", "final: $text")
                process(text, voice = true)
            }

            override fun onRms(level: Float) {
                AssistantState.amplitude.value = level
            }

            override fun onError(error: Int) {
                cancelListenWatchdog()
                Diag.log("asr", "error: ${describeAsrError(error)}")
                onListenError(error)
            }

            override fun onEnd() {
                // 后续交给 onFinal / onError
            }
        })
        armListenWatchdog()
    }

    private fun onListenError(error: Int) {
        feedback.error()
        if (turns > 0) {
            AssistantState.status.value = "没有听清，结束对话"
        } else {
            AssistantState.status.value = "识别出错：${describeAsrError(error)}"
        }
        finishConversation()
    }

    // ---- 看门狗 ----

    private fun armListenWatchdog() {
        cancelListenWatchdog()
        val runnable = Runnable {
            if (listening) {
                Diag.log("watchdog", "ASR timeout")
                onListenError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            }
        }
        listenWatchdog = runnable
        mainHandler.postDelayed(runnable, LISTEN_TIMEOUT_MS)
    }

    private fun cancelListenWatchdog() {
        listenWatchdog?.let { mainHandler.removeCallbacks(it) }
        listenWatchdog = null
    }

    // ---- 回答 ----

    private fun process(text: String, voice: Boolean) {
        if (text.isBlank()) {
            finishConversation()
            return
        }
        if (isEndPhrase(text)) {
            AssistantState.status.value = "好的，结束对话"
            tts.speak("好的，再见")
            finishConversation()
            return
        }

        AssistantState.status.value = "思考中…"
        scope.launch {
            Diag.log("agent", "request: ${text.take(60)}")
            val reply = agent.respond(text)
            Diag.log("agent", "reply: ${reply.take(80)}")
            AssistantState.answer.value = reply
            AssistantState.status.value = "完成"
            speakWithWatchdog(reply, voice)
        }
    }

    /** 播报回答；若 TTS 回调迟迟不来，看门狗兜底继续流程。 */
    private fun speakWithWatchdog(reply: String, voice: Boolean) {
        var handled = false
        val timeout = Runnable {
            if (!handled) {
                handled = true
                Diag.log("watchdog", "TTS timeout")
                if (voice) onSpoken() else finishConversation()
            }
        }
        mainHandler.postDelayed(timeout, TTS_TIMEOUT_MS)
        tts.speak(reply) {
            if (!handled) {
                handled = true
                mainHandler.removeCallbacks(timeout)
                if (voice) onSpoken() else finishConversation()
            }
        }
    }

    /** 文字调试：不经过麦克风，直接走同一套「大脑」。 */
    private fun handleTextCommand(text: String) {
        if (text.isBlank()) {
            stopEverything()
            return
        }
        detector.stop()
        listening = false
        turns = 0
        AssistantState.heard.value = text
        Diag.log("text", "command: ${text.take(60)}")
        process(text, voice = false)
    }

    /** 一条回答播报完毕。 */
    private fun onSpoken() {
        listening = false
        if (wakeMode && settings.continuousConversation && turns < MAX_TURNS) {
            turns++
            requestListen()
        } else {
            finishConversation()
        }
    }

    private fun finishConversation() {
        cancelListenWatchdog()
        listening = false
        turns = 0
        AssistantState.amplitude.value = 0f
        if (wakeMode) {
            startWake()
        } else {
            stopEverything()
        }
    }

    private fun isEndPhrase(text: String): Boolean {
        val t = text.trim()
        if (t.length > 12) return false
        return Regex("结束|退出|不聊了|不说了|再见|拜拜|没有别的事|没别的|没有了|不用了")
            .containsMatchIn(t)
    }

    // ---- 生命周期 ----

    private fun stopEverything() {
        cancelListenWatchdog()
        detector.stop()
        asr.destroy()
        tts.shutdown()
        feedback.release()
        AssistantState.amplitude.value = 0f
        AssistantState.running.value = false
        AssistantState.status.value = "已停止"
        Diag.log("service", "stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        cancelListenWatchdog()
        detector.stop()
        tts.shutdown()
        feedback.release()
        AssistantState.running.value = false
        Diag.log("service", "destroyed")
        super.onDestroy()
    }

    // ---- 通知 ----

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "语音助手",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val listenPending = PendingIntent.getService(
            this,
            10,
            Intent(this, WakeWordService::class.java).setAction(ACTION_LISTEN_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            this,
            11,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音助手运行中")
            .setContentText("正在监听唤醒词「${settings.wakeWord}」")
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(contentIntent)
            .addAction(0, "聆听", listenPending)
            .addAction(0, "停止", stopPending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_WAKE = "com.demian.voiceassistant.action.START_WAKE"
        const val ACTION_LISTEN_NOW = "com.demian.voiceassistant.action.LISTEN_NOW"
        const val ACTION_STOP = "com.demian.voiceassistant.action.STOP"
        const val ACTION_TEXT_COMMAND = "com.demian.voiceassistant.action.TEXT_COMMAND"
        const val EXTRA_TEXT = "com.demian.voiceassistant.extra.TEXT"

        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_TURNS = 6
        private const val LISTEN_TIMEOUT_MS = 30_000L
        private const val TTS_TIMEOUT_MS = 60_000L

        /** 把 SpeechRecognizer 的错误码翻译成人话。 */
        private fun describeAsrError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "录音错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少权限"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话"
            else -> "未知错误($error)"
        }
    }
}
