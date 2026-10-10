package com.demian.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.demian.voiceassistant.asr.SpeechRecognizerManager
import com.demian.voiceassistant.agent.CommandRouter
import com.demian.voiceassistant.phone.PhoneInfoProvider
import com.demian.voiceassistant.tts.TtsManager
import com.demian.voiceassistant.ui.theme.VoiceAssistantTheme

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

    val tts = remember { TtsManager(context) }
    val asr = remember { SpeechRecognizerManager(context) }
    val router = remember { CommandRouter(PhoneInfoProvider(context)) }
    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
            asr.destroy()
        }
    }

    var status by mutableStateOf("待机")
    var result by mutableStateOf("")
    var answer by mutableStateOf("")
    var listening by mutableStateOf(false)

    fun startListening() {
        listening = true
        status = "正在聆听…"
        result = ""
        asr.start(object : SpeechRecognizerManager.Callback {
            override fun onReady() {
                status = "请说话…"
            }

            override fun onPartial(text: String) {
                result = text
            }

            override fun onFinal(text: String) {
                result = text
                val reply = router.route(text)
                answer = reply ?: "这个我暂时还不会，接入大模型后就能回答啦。"
                status = "识别完成"
                if (answer.isNotBlank()) tts.speak(answer)
            }

            override fun onError(error: Int) {
                status = "识别出错：${describeAsrError(error)}"
            }

            override fun onEnd() {
                listening = false
            }
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            status = "需要麦克风权限才能识别语音"
        }
    }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            text = "语音助手 · 阶段 3（听 + 说）",
            style = MaterialTheme.typography.titleLarge
        )

        Text(text = "状态：$status", style = MaterialTheme.typography.bodyMedium)

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
            Text(
                text = if (result.isBlank()) "（识别结果会显示在这里）" else result,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (answer.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("助手回答", style = MaterialTheme.typography.labelMedium)
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Button(
            onClick = { tts.speak(result) },
            enabled = result.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔊 播报识别结果")
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
