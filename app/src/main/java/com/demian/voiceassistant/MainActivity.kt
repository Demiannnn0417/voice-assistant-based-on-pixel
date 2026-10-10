package com.demian.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.collectAsState
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
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.knowledge.KnowledgeImporter
import com.demian.voiceassistant.knowledge.KnowledgeStore
import com.demian.voiceassistant.service.AssistantState
import com.demian.voiceassistant.service.WakeWordService
import com.demian.voiceassistant.ui.theme.VoiceAssistantTheme
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

    val settings = remember { SettingsStore(context) }
    val knowledge = remember { KnowledgeStore(context) }

    val status by AssistantState.status.collectAsState()
    val heard by AssistantState.heard.collectAsState()
    val answer by AssistantState.answer.collectAsState()
    val running by AssistantState.running.collectAsState()

    var apiKeyInput by remember { mutableStateOf(settings.deepSeekApiKey) }
    var docCount by remember { mutableStateOf(knowledge.chunkCount()) }

    fun hasMic(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun startWakeService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_START_WAKE)
        )
    }

    fun stopWakeService() {
        context.startService(
            Intent(context, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_STOP)
        )
    }

    fun listenNow() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_LISTEN_NOW)
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWakeService()
        else AssistantState.status.value = "需要麦克风权限才能开启唤醒"
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startWakeService() }

    fun enableWake() {
        if (!hasMic()) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startWakeService()
    }

    val micLauncherListen = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listenNow()
        else AssistantState.status.value = "需要麦克风权限才能识别语音"
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = KnowledgeImporter.import(context, knowledge, uri)
                docCount = knowledge.chunkCount()
                AssistantState.status.value =
                    if (n > 0) "已导入 $n 个片段" else "文件为空或读取失败"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "语音助手", style = MaterialTheme.typography.titleLarge)
        Text(text = "状态：$status", style = MaterialTheme.typography.bodyMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("常驻唤醒（后台/息屏可用）")
                Text(
                    "唤醒词：你好小楠",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = running,
                onCheckedChange = { checked ->
                    if (checked) enableWake() else stopWakeService()
                }
            )
        }

        Button(
            onClick = {
                if (hasMic()) listenNow()
                else micLauncherListen.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎤 立刻聆听一次")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("你说", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = heard.ifBlank { "（识别结果显示在这里）" },
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
                AssistantState.status.value =
                    if (apiKeyInput.isBlank()) "已清空 API Key" else "已保存 API Key"
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
                AssistantState.status.value = "已清空知识库"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🗑 清空知识库")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AssistantScreenPreview() {
    VoiceAssistantTheme {
        AssistantScreen()
    }
}
