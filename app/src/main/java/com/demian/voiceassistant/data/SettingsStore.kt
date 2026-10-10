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

    companion object {
        private const val KEY_DEEPSEEK = "deepseek_api_key"
        private const val KEY_MODEL = "deepseek_model"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
