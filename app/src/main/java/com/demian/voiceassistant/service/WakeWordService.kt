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
 * 职责：持有麦克风与自动化流程 —— 唤醒词检测 → 语音识别 → 助手回答 → 语音播报，
 * 播报结束后重新回到唤醒监听。界面只负责控制它并显示状态。
 */
class WakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var detector: WakeWordDetector
    private lateinit var asr: SpeechRecognizerManager
    private lateinit var tts: TtsManager
    private lateinit var agent: AssistantAgent
    private lateinit var settings: SettingsStore

    private var wakeMode = false
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        detector = WakeWordDetector(this)
        asr = SpeechRecognizerManager(this)
        tts = TtsManager(this)
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        // 任何其他 action 都要先进前台，否则系统会杀
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_LISTEN_NOW -> {
                // 保留当前 wakeMode：若是从常驻模式手动触发，播报后仍回到唤醒监听
                requestListen()
            }
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
        val word = settings.wakeWord
        AssistantState.status.value = "常驻监听中（说「$word」）"
        val keywords = PinyinKeywords.keywordsOrDefault(this, word)
        detector.start(keywords) { keyword -> mainHandler.post { onWake(keyword) } }
    }

    private fun onWake(keyword: String) {
        if (!wakeMode || listening) return
        AssistantState.status.value = "已唤醒：$keyword"
        tts.speak("在")
        requestListen()
    }

    // ---- 识别 ----

    private fun requestListen() {
        detector.stop() // 让出麦克风给识别
        if (listening) return
        listening = true
        AssistantState.heard.value = ""
        AssistantState.status.value = "正在聆听…"
        asr.start(object : SpeechRecognizerManager.Callback {
            override fun onReady() {
                AssistantState.status.value = "请说话…"
            }

            override fun onPartial(text: String) {
                AssistantState.heard.value = text
            }

            override fun onFinal(text: String) {
                AssistantState.heard.value = text
                process(text)
            }

            override fun onError(error: Int) {
                AssistantState.status.value = "识别出错：${describeAsrError(error)}"
                finishListening()
            }

            override fun onEnd() {
                // 后续流程交给 onFinal / onError
            }
        })
    }

    // ---- 回答 ----

    private fun process(text: String) {
        if (text.isBlank()) {
            AssistantState.status.value = "没有听清"
            finishListening()
            return
        }
        AssistantState.status.value = "思考中…"
        scope.launch {
            val reply = agent.respond(text)
            AssistantState.answer.value = reply
            AssistantState.status.value = "完成"
            // 播报结束后再恢复唤醒，避免把自己的声音当成唤醒词
            tts.speak(reply) { finishListening() }
        }
    }

    private fun finishListening() {
        listening = false
        if (wakeMode) {
            startWake()
        } else {
            stopEverything()
        }
    }

    // ---- 生命周期 ----

    private fun stopEverything() {
        detector.stop()
        asr.destroy()
        tts.shutdown()
        AssistantState.running.value = false
        AssistantState.status.value = "已停止"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        detector.stop()
        tts.shutdown()
        AssistantState.running.value = false
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音助手运行中")
            .setContentText("正在监听唤醒词「${settings.wakeWord}」")
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_WAKE = "com.demian.voiceassistant.action.START_WAKE"
        const val ACTION_LISTEN_NOW = "com.demian.voiceassistant.action.LISTEN_NOW"
        const val ACTION_STOP = "com.demian.voiceassistant.action.STOP"

        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIFICATION_ID = 1001

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
