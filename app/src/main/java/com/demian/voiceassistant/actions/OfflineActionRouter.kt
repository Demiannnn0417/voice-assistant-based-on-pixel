package com.demian.voiceassistant.actions

import org.json.JSONObject

/** 一条可直接执行的设备命令。 */
data class DeviceCommand(val name: String, val args: JSONObject)

/**
 * 离线动作路由：用关键词把常见操作直接映射成设备命令，
 * **不需要大模型 / API Key / 网络**。
 */
class OfflineActionRouter {

    fun route(text: String): DeviceCommand? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 24) return null

        return when {
            Regex("(关闭|关掉|关)(手电|手电筒|闪光灯)").containsMatchIn(t) ->
                DeviceCommand("set_flashlight", JSONObject().put("on", false))

            Regex("(打开|开)(手电|手电筒|闪光灯)").containsMatchIn(t) ->
                DeviceCommand("set_flashlight", JSONObject().put("on", true))

            Regex("音量(最大|调到最大|开到最大|最大声)").containsMatchIn(t) ->
                DeviceCommand("set_volume", JSONObject().put("action", "max"))

            Regex("音量(大|加|调大)|大声点|声音大点").containsMatchIn(t) ->
                DeviceCommand("set_volume", JSONObject().put("action", "up"))

            Regex("音量(小|减|调小)|小声点|声音小点").containsMatchIn(t) ->
                DeviceCommand("set_volume", JSONObject().put("action", "down"))

            Regex("静音|关闭声音").containsMatchIn(t) ->
                DeviceCommand("set_volume", JSONObject().put("action", "mute"))

            Regex("下一首|下首|切歌").containsMatchIn(t) ->
                DeviceCommand("media_control", JSONObject().put("action", "next"))

            Regex("上一首|上首").containsMatchIn(t) ->
                DeviceCommand("media_control", JSONObject().put("action", "previous"))

            Regex("暂停|停一下").containsMatchIn(t) ->
                DeviceCommand("media_control", JSONObject().put("action", "pause"))

            Regex("播放|继续播放|放音乐").containsMatchIn(t) ->
                DeviceCommand("media_control", JSONObject().put("action", "play"))

            else -> null
        }
    }
}
