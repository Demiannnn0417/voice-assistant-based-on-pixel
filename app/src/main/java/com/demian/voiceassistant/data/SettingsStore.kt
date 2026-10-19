package com.demian.voiceassistant.data

import android.content.Context

/**
 * 本地设置存储。用 SharedPreferences 保存 API Key 等敏感信息，
 * 不写入代码、不提交到仓库。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    /** DeepSeek API Key。 */
    var deepSeekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEEPSEEK, value.trim()).apply()

    /** 使用的模型名。 */
    var deepSeekModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** 自定义唤醒词（中文）。 */
    var wakeWord: String
        get() = prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit().putString(KEY_WAKE_WORD, value.trim()).apply()

    /** 连续对话：回答完自动继续听，不用每次唤醒。 */
    var continuousConversation: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    /** 主题：system / light / dark。 */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    companion object {
        private const val KEY_DEEPSEEK = "deepseek_api_key"
        private const val KEY_MODEL = "deepseek_model"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_CONTINUOUS = "continuous_conversation"
        private const val KEY_THEME = "theme_mode"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_WAKE_WORD = "你好小楠"
    }
}
