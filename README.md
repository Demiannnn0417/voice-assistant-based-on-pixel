# VoiceAssistant（语音助手）

一个运行在 Android（Pixel 9 Pro）上的实时语音助手 App。

## 功能与进度

| 阶段 | 功能 | 状态 |
|------|------|------|
| 2 | 语音播报（TTS） | ✅ |
| 3 | 语音识别（ASR） | ✅ |
| 4 | 唤醒词（sherpa-onnx 离线 KWS） | ✅ |
| 5 | 云端大模型（DeepSeek） | ✅ |
| 6 | 本地知识库（导入文件 + 检索） | ✅ |
| 7 | 读取手机信息（时间/日期/电量） | ✅ |
| 8 | 常驻前台服务（后台/息屏唤醒） | ✅ |
| 9 | 工具调用（闹钟 / 打开应用 / 媒体 / 手电筒 / 音量 / 拨号 / 便签） | ✅ |
| 10 | 多轮对话与记忆（对话记录页） | ✅ |
| 11 | 应用内自定义唤醒词（中文自动转拼音） | ✅ |
| 12 | 连续对话（回答后自动继续听，说「结束」退出） | ✅ |
| 13 | 提示音 / 震动反馈 | ✅ |
| 14 | 文字调试模式（无需麦克风即可测试助手） | ✅ |
| 15 | 单元测试（23 条）+ GitHub Actions CI | ✅ |
| 16 | 更多工具（天气 / 系统设置 / 日程 / 震动） | ✅ |
| 17 | 知识库增强（多文件导入 / 按文件删除 / 分块重叠 / BM25 打分） | ✅ |
| 18 | 通知栏按钮 / 实时音量波形 / 主题切换 / 配置引导 | ✅ |
| 19 | Release 签名 + R8 keep 规则（可安装正式包） | ✅ |
| 20 | 离线本地动作（手电筒 / 音量 / 媒体，免 Key） | ✅ |

## 使用流程（当前版本）

1. 在 App「设置」里填入 **DeepSeek API Key** 并保存
2. 打开 **「常驻唤醒」** 开关，允许麦克风权限 —— App 会在后台/息屏持续听
3. 说 **「你好小楠」** → 语音回「在」→ 接着说命令
   - 也可以点「🎤 立刻聆听一次」直接说话
4. 助手先尝试本地回答（时间/日期/电量），否则交给 DeepSeek
5. 回答会**显示并语音播报**
6. 可导入 `.txt / .md` 文件作为**本地知识库**，回答时自动检索引用
7. App 里有 **对话记录**、**知识库**、**设置** 三个页面（底部导航）
8. 在「设置」里可**自定义唤醒词**（输入中文即可，自动转拼音；需词表支持）
9. 助手能**执行动作**：设闹钟/计时、打开应用、控制媒体、开关手电筒、调音量、打开拨号、记便签

## 技术栈

| 项 | 选型 |
|----|------|
| 语言 / UI | Kotlin + Jetpack Compose |
| 构建 | Gradle (Kotlin DSL) + AGP 9.4.1 |
| 唤醒词 | sherpa-onnx 离线关键词唤醒，唤醒词「你好小楠」 |
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

## 测试

```bash
./gradlew testDebugUnitTest    # 单元测试（拼音/检索/路由/JSON/工具）
./gradlew assembleDebug        # 打包
```

- CI：`.github/workflows/android.yml`，push 后自动跑测试并产出 APK 构建产物
- 无真机时可用 **Android 模拟器**（arm64 镜像）验证界面、前台服务与文字调试模式

## 正式包与签名

Release 包使用本地 keystore 签名（文件均在仓库外）：

- 密钥：`~/.config/voiceassistant/release.keystore`
- 配置：`~/.config/voiceassistant/keystore.properties`

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

⚠️ **务必备份上面两个文件**。丢失后无法覆盖安装升级（只能卸载重装）。

> 说明：AGP 9 的 `optimization`（R8 类合并）在本项目会触发
> `kotlin.text` 的 `IllegalAccessError`，因此 release 构建中已关闭该优化。
> JNI 相关的 keep 规则见 `app/src/main/keepRules/rules.keep`。

## 密钥说明

DeepSeek API Key 在 App 内填写，保存在设备本地（SharedPreferences），
**不会提交到仓库**。`local.properties`、`secrets.properties` 等也已在 `.gitignore` 中忽略。
