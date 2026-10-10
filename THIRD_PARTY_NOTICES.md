# 第三方组件说明

## sherpa-onnx

- 项目：https://github.com/k2-fsa/sherpa-onnx
- 许可：Apache License 2.0
- 用途：离线关键词唤醒（KWS）
- 引入方式：
  - `app/src/main/jniLibs/arm64-v8a/*.so`（原生库）
  - `app/src/main/java/com/k2fsa/sherpa/onnx/*.kt`（Kotlin API 封装，来自其 `kotlin-api/`）
  - `app/src/main/assets/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/`（中文 KWS 模型）

模型版权归原始训练者所有，详见模型目录说明。
