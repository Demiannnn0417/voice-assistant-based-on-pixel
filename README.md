# VoiceAssistant（语音助手）

一个运行在 Android（Pixel 9 Pro）上的实时语音助手 App。

## 规划中的功能

- 自定义唤醒词（Picovoice Porcupine），支持个性化称呼（类似 “Hey Siri”）
- 唤醒后聆听语音命令
- 接入云端大模型理解命令
- 读取本地知识库（导入的文件）与手机信息
- 通过 TTS 语音播报结果

## 技术栈

| 项 | 选型 |
|----|------|
| 语言 / UI | Kotlin + Jetpack Compose |
| 构建 | Gradle (Kotlin DSL) + AGP |
| 唤醒词 | Picovoice Porcupine |
| 语音识别 | Android SpeechRecognizer |
| 语音合成 | Android TextToSpeech |
| 命令理解 | 云端大模型 API |
| 最低版本 | Android 10 (API 29) |

## 环境要求

- Android Studio
- Android SDK（Platform 37 / Build-Tools 36）
- JDK（Android Studio 自带即可）

## 构建

```bash
./gradlew assembleDebug
```

或用 Android Studio 直接 Run。

## 密钥说明

Porcupine AccessKey、云端大模型 API Key 等敏感信息**不提交到仓库**，
请放在 `local.properties` 或 `secrets.properties`（均已在 `.gitignore` 中忽略）。
