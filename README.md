# VoiceAssistant（语音助手）

一个运行在 Android（Pixel 9 Pro）上的实时语音助手 App。

## 功能与进度

| 阶段 | 功能 | 状态 |
|------|------|------|
| 2 | 语音播报（TTS） | ✅ |
| 3 | 语音识别（ASR） | ✅ |
| 4 | 唤醒词（Porcupine） | ⏳ 待做 |
| 5 | 云端大模型（DeepSeek） | ✅ |
| 6 | 本地知识库（导入文件 + 检索） | ✅ |
| 7 | 读取手机信息（时间/日期/电量） | ✅ |
| 8 | 常驻前台服务 | ⏳ 待做 |

## 使用流程（当前版本）

1. 在 App「设置」里填入 **DeepSeek API Key** 并保存
2. 点 **🎤 开始聆听**，允许麦克风权限
3. 说话——助手会先尝试本地回答（时间/日期/电量），否则交给 DeepSeek
4. 回答会**显示并语音播报**
5. 可导入 `.txt / .md` 文件作为**本地知识库**，回答时自动检索引用

## 技术栈

| 项 | 选型 |
|----|------|
| 语言 / UI | Kotlin + Jetpack Compose |
| 构建 | Gradle (Kotlin DSL) + AGP 9.4.1 |
| 唤醒词 | Picovoice Porcupine（待接入） |
| 语音识别 | Android SpeechRecognizer |
| 语音合成 | Android TextToSpeech |
| 命令理解 | DeepSeek（OpenAI 兼容接口） |
| 本地存储 | SharedPreferences + SQLite（系统自带，无额外依赖） |
| 最低版本 | Android 10 (API 29) |

## 构建

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

或用 Android Studio 直接 Run。

## 密钥说明

DeepSeek API Key 在 App 内填写，保存在设备本地（SharedPreferences），
**不会提交到仓库**。`local.properties`、`secrets.properties` 等也已在 `.gitignore` 中忽略。
