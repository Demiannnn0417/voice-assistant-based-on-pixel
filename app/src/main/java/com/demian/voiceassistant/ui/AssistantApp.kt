package com.demian.voiceassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.demian.voiceassistant.R
import com.demian.voiceassistant.data.ConversationStore
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.knowledge.KnowledgeImporter
import com.demian.voiceassistant.knowledge.KnowledgeStore
import com.demian.voiceassistant.service.AssistantState
import com.demian.voiceassistant.service.WakeWordService
import com.demian.voiceassistant.ui.screens.ConversationScreen
import com.demian.voiceassistant.ui.screens.HomeScreen
import com.demian.voiceassistant.ui.screens.KnowledgeScreen
import com.demian.voiceassistant.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun AssistantApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings = remember { SettingsStore(context) }
    val knowledge = remember { KnowledgeStore(context) }
    val conversation = remember { ConversationStore(context) }

    val status by AssistantState.status.collectAsState()
    val heard by AssistantState.heard.collectAsState()
    val answer by AssistantState.answer.collectAsState()
    val running by AssistantState.running.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var docCount by remember { mutableStateOf(knowledge.chunkCount()) }
    var sources by remember { mutableStateOf(knowledge.sources()) }
    var wakeWord by remember { mutableStateOf(settings.wakeWord) }
    var messages by remember { mutableStateOf(conversation.all()) }

    // 每次有新回答就刷新对话记录
    LaunchedEffect(answer) {
        messages = conversation.all()
    }

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
                sources = knowledge.sources()
                AssistantState.status.value =
                    if (n > 0) "已导入 $n 个片段" else "文件为空或读取失败"
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_nav_home), contentDescription = null) },
                    label = { Text("主页") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_nav_chat), contentDescription = null) },
                    label = { Text("对话") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(painterResource(R.drawable.ic_nav_knowledge), contentDescription = null) },
                    label = { Text("知识库") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (tab) {
                0 -> HomeScreen(
                    status = status,
                    heard = heard,
                    answer = answer,
                    running = running,
                    wakeWord = wakeWord,
                    onToggleWake = { checked -> if (checked) enableWake() else stopWakeService() },
                    onListenNow = {
                        if (hasMic()) listenNow()
                        else micLauncherListen.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )

                1 -> ConversationScreen(
                    messages = messages,
                    onClear = {
                        conversation.clear()
                        messages = emptyList()
                        AssistantState.status.value = "已清空对话记录"
                    },
                )

                2 -> KnowledgeScreen(
                    docCount = docCount,
                    sources = sources,
                    onImport = { importLauncher.launch(arrayOf("text/*")) },
                    onClear = {
                        knowledge.clear()
                        docCount = 0
                        sources = emptyList()
                        AssistantState.status.value = "已清空知识库"
                    },
                )

                else -> SettingsScreen(
                    settings = settings,
                    onWakeWordChanged = { wakeWord = it },
                    onClearConversation = {
                        conversation.clear()
                        messages = emptyList()
                    },
                )
            }
        }
    }
}
