package com.demian.voiceassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.wake.PinyinKeywords

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onWakeWordChanged: (String) -> Unit,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var apiKey by remember { mutableStateOf(settings.deepSeekApiKey) }
    var model by remember { mutableStateOf(settings.deepSeekModel) }
    var saved by remember { mutableStateOf(false) }

    var wakeWord by remember { mutableStateOf(settings.wakeWord) }
    var wakeMsg by remember { mutableStateOf("") }
    var wakeOk by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(settings.continuousConversation) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        // --- 大模型 ---
        Text("大模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saved = false
            },
            label = { Text("DeepSeek API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                saved = false
            },
            label = { Text("模型名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                settings.deepSeekApiKey = apiKey
                settings.deepSeekModel = model
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("💾 保存")
        }

        if (saved) {
            Text(
                text = "已保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // --- 唤醒词 ---
        Text("唤醒词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = wakeWord,
            onValueChange = {
                wakeWord = it
                wakeMsg = ""
            },
            label = { Text("自定义唤醒词（中文）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val vocab = runCatching { PinyinKeywords.vocab(context) }.getOrDefault(emptySet())
                when (val r = PinyinKeywords.toKeywords(wakeWord, vocab)) {
                    is PinyinKeywords.Result.Ok -> {
                        settings.wakeWord = wakeWord.trim()
                        onWakeWordChanged(wakeWord.trim())
                        wakeOk = true
                        wakeMsg = "已保存。下次开启唤醒生效。"
                    }

                    is PinyinKeywords.Result.Invalid -> {
                        wakeOk = false
                        wakeMsg = r.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存唤醒词")
        }

        if (wakeMsg.isNotBlank()) {
            Text(
                text = wakeMsg,
                style = MaterialTheme.typography.labelMedium,
                color = if (wakeOk) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        OutlinedButton(
            onClick = onClearConversation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("🗑 清空对话记录")
        }

        // --- 交互 ---
        Text("交互", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("连续对话", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "回答后自动继续听下一句，说「结束」退出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = continuous,
                onCheckedChange = {
                    continuous = it
                    settings.continuousConversation = it
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // --- 关于 ---
        Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                InfoRow("唤醒引擎", "sherpa-onnx（离线）")
                InfoDivider()
                InfoRow("语音识别", "系统 SpeechRecognizer")
                InfoDivider()
                InfoRow("对话模型", settings.deepSeekModel)
                InfoDivider()
                InfoRow("版本", "v1.3")
            }
        }

        Text(
            text = "github.com/Demiannnn0417/voice-assistant-based-on-pixel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/Demiannnn0417/voice-assistant-based-on-pixel")
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}
