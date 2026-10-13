package com.demian.voiceassistant.actions

import org.json.JSONArray
import org.json.JSONObject

/**
 * 可用「工具」的声明，交给大模型（OpenAI 函数调用格式）。
 * 描述写清楚，模型才能正确选择与填参。
 */
object ToolRegistry {

    fun tools(): JSONArray {
        val arr = JSONArray()
        arr.put(fn("set_alarm", "设置闹钟。hour/minute 用 24 小时制。", params(
            required = listOf("hour", "minute"),
            "hour" to int("小时，0-23"),
            "minute" to int("分钟，0-59"),
            "label" to str("闹钟备注，可省略"),
        )))
        arr.put(fn("set_timer", "设置倒计时（计时器）。", params(
            required = listOf("seconds"),
            "seconds" to int("倒计时秒数"),
            "label" to str("备注，可省略"),
        )))
        arr.put(fn("open_app", "打开手机上的某个应用。", params(
            required = listOf("app_name"),
            "app_name" to str("应用名称，例如「微信」「相机」"),
        )))
        arr.put(fn("open_url", "用浏览器打开一个网址。", params(
            required = listOf("url"),
            "url" to str("网址"),
        )))
        arr.put(fn("media_control", "控制正在播放的媒体。", params(
            required = listOf("action"),
            "action" to enum("操作类型", "play", "pause", "next", "previous", "stop"),
        )))
        arr.put(fn("set_flashlight", "打开或关闭手电筒。", params(
            required = listOf("on"),
            "on" to bool("true 打开，false 关闭"),
        )))
        arr.put(fn("set_volume", "调节媒体音量。", params(
            required = listOf("action"),
            "action" to enum("操作类型", "up", "down", "mute", "unmute", "max"),
        )))
        arr.put(fn("make_call", "拨打一个电话号码（会打开拨号界面）。", params(
            required = listOf("target"),
            "target" to str("电话号码"),
        )))
        arr.put(fn("save_note", "记一条便签 / 备忘。", params(
            required = listOf("text"),
            "text" to str("要记录的内容"),
        )))
        arr.put(fn("list_notes", "列出所有已记录的便签。", params(required = emptyList())))
        return arr
    }

    private fun fn(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", parameters)
            })
        }

    private fun params(
        required: List<String>,
        vararg props: Pair<String, JSONObject>,
    ): JSONObject {
        val properties = JSONObject()
        props.forEach { (key, value) -> properties.put(key, value) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
    }

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun int(desc: String) = JSONObject().put("type", "integer").put("description", desc)
    private fun bool(desc: String) = JSONObject().put("type", "boolean").put("description", desc)

    private fun enum(desc: String, vararg values: String) =
        JSONObject()
            .put("type", "string")
            .put("description", desc)
            .put("enum", JSONArray(values.toList()))
}
