package com.demian.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.demian.voiceassistant.agent.AssistantAgent
import com.demian.voiceassistant.agent.CommandRouter
import com.demian.voiceassistant.asr.SpeechRecognizerManager
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.knowledge.KnowledgeImporter
import com.demian.voiceassistant.knowledge.KnowledgeRetriever
import com.demian.voiceassistant.knowledge.KnowledgeStore
import com.demian.voiceassistant.llm.DeepSeekClient
import com.demian.voiceassistant.phone.PhoneInfoProvider
import com.demian.voiceassistant.tts.TtsManager
import com.demian.voiceassistant.ui.theme.VoiceAssistantTheme
import com.demian.voiceassistant.wake.WakeWordDetector
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AssistantScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val tts = remember { TtsManager(context) }
    val asr = remember { SpeechRecognizerManager(context) }
    val detector = remember { WakeWordDetector(context) }
    val settings = remember { SettingsStore(context) }
    val phone = remember { PhoneInfoProvider(context) }
    val knowledge = remember { KnowledgeStore(context) }
    val retriever = remember { KnowledgeRetriever(knowledge) }
    val agent = remember {
        AssistantAgent(CommandRouter(phone), DeepSeekClient(settings), phone, retriever)
    }
    DisposableEffect(Unit) {
        onDispose {
            detector.stop()
            tts.shutdown()
            asr.destroy()
        }
    }

    var status by mutableStateOf("待机")
    var result by mutableStateOf("")
    var answer by mutableStateOf("")
    var listening by mutableStateOf(false)
    var wakeEnabled by mutableStateOf(false)
    var apiKeyInput by remember { mutableStateOf(settings.deepSeekApiKey) }
    var docCount by remember { mutableStateOf(knowledge.chunkCount()) }

    fun handleCommand(command: String) {
        status = "思考中…"
        scope.launch {
            val reply = agent.respond(command)
            answer = reply
            status = "完成"
            if (reply.isNotBlank()) tts.speak(reply)
        }
    }

    fun startListening() {
        if (listening) return
        listening = true
        status = "正在聆听…"
        result = ""
        answer = ""
        asr.start(object : SpeechRecognizerManager.Callback {
            override fun onReady() {
                status = "请说话…"
            }

            override fun onPartial(text: String) {
                result = text
            }

            override fun onFinal(text: String) {
                result = text
                listening = false
                handleCommand(text)
            }

            override fun onError(error: Int) {
                status = "识别出错：${describeAsrError(error)}"
            }

            override fun onEnd() {
                listening = false
            }
        })
    }

    // 唤醒词命中（在后台线程回调，切回主线程）
    fun onWake(keyword: String) {
        mainHandler.post {
            if (!wakeEnabled || listening) return@post
            status = "已唤醒：$keyword"
            tts.speak("在")
            startListening()
        }
    }

    // 常驻唤醒：当「开启唤醒且未在识别」时监听，其余情况释放麦克风
    DisposableEffect(wakeEnabled, listening) {
        if (wakeEnabled && !listening) {
            detector.start { keyword -> onWake(keyword) }
        }
        onDispose { detector.stop() }
    }

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            status = "需要麦克风权限才能识别语音"
        }
    }

    val wakePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wakeEnabled = true
        } else {
            status = "需要麦克风权限才能开启唤醒"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            status = "正在导入…"
            scope.launch {
                val n = KnowledgeImporter.import(context, knowledge, uri)
                docCount = knowledge.chunkCount()
                status = if (n > 0) "已导入 $n 个片段" else "文件为空或读取失败"
            }
        }
    }

    fun onMicClick() {
        if (hasMicPermission()) {
            startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onWakeToggle(enable: Boolean) {
        if (enable && !hasMicPermission()) {
            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            wakeEnabled = enable
            status = if (enable) "已开启常驻唤醒，说「你好小明」" else "已关闭常驻唤醒"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "语音助手 · 阶段 4（唤醒词）",
            style = MaterialTheme.typography.titleLarge
        )

        Text(text = "状态：$status", style = MaterialTheme.typography.bodyMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("常驻唤醒（说「你好小明」）")
            Switch(checked = wakeEnabled, onCheckedChange = { onWakeToggle(it) })
        }

        Button(
            onClick = { onMicClick() },
            enabled = !listening,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (listening) "🎙 聆听中…" else "🎤 开始聆听")
        }

        if (listening) {
            OutlinedButton(
                onClick = { asr.stop() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏹ 我说完了")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("你说", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (result.isBlank()) "（识别结果显示在这里）" else result,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (answer.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("助手回答", style = MaterialTheme.typography.labelMedium)
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        OutlinedButton(
            onClick = { if (answer.isNotBlank() || result.isNotBlank()) tts.speak(answer.ifBlank { result }) },
            enabled = answer.isNotBlank() || result.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔊 重播")
        }

        Text("设置", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("DeepSeek API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                settings.deepSeekApiKey = apiKeyInput
                status = if (apiKeyInput.isBlank()) "已清空 API Key" else "已保存 API Key"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 保存 Key")
        }

        Text("本地知识库", style = MaterialTheme.typography.titleMedium)

        Text("已导入 $docCount 个片段", style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = { importLauncher.launch(arrayOf("text/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📄 导入文本文件（.txt / .md）")
        }

        OutlinedButton(
            onClick = {
                knowledge.clear()
                docCount = 0
                status = "已清空知识库"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🗑 清空知识库")
        }
    }
}

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

@Preview(showBackground = true)
@Composable
fun AssistantScreenPreview() {
    VoiceAssistantTheme {
        AssistantScreen()
    }
}
