package com.demian.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.demian.voiceassistant.tts.TtsManager
import com.demian.voiceassistant.ui.theme.VoiceAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TtsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TtsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // remember：只在第一次组合时创建，重组时复用
    val tts = remember { TtsManager(context) }
    // 页面销毁时释放 TTS 资源
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    var input by mutableStateOf("你好，我是你的语音助手")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "阶段 2 · 语音播报测试",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入要播报的文字") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { tts.speak(input) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔊 播报")
        }

        Spacer(Modifier.height(8.dp))

        Text("快捷测试：", style = MaterialTheme.typography.labelLarge)
        listOf(
            "现在是几点？",
            "你好，我在听。",
            "这是一段中文语音合成测试。"
        ).forEach { sample ->
            OutlinedButton(
                onClick = { tts.speak(sample) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(sample)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TtsScreenPreview() {
    VoiceAssistantTheme {
        TtsScreen()
    }
}
