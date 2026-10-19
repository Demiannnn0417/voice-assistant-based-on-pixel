package com.demian.voiceassistant.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import com.demian.voiceassistant.data.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

/**
 * 设备动作执行器：把大模型选择的「工具」翻译成真实的系统操作。
 *
 * 带界面的操作在后台可能被 Android 限制，这里统一 try/catch 并返回说明。
 * 天气查询需要网络，所以 [execute] 是 suspend 函数。
 */
class DeviceActions(
    private val context: Context,
    private val notes: NotesStore,
) {

    suspend fun execute(name: String, args: JSONObject): String = try {
        when (name) {
            "set_alarm" -> setAlarm(args)
            "set_timer" -> setTimer(args)
            "open_app" -> openApp(args)
            "open_url" -> openUrl(args)
            "media_control" -> mediaControl(args)
            "set_flashlight" -> setFlashlight(args)
            "set_volume" -> setVolume(args)
            "make_call" -> makeCall(args)
            "save_note" -> saveNote(args)
            "list_notes" -> listNotes()
            "get_weather" -> getWeather(args)
            "open_settings" -> openSettings(args)
            "create_calendar_event" -> createCalendarEvent(args)
            "vibrate" -> vibrate(args)
            else -> "不支持的操作：$name"
        }
    } catch (t: Throwable) {
        "操作失败：${t.message ?: t.javaClass.simpleName}"
    }

    private fun setAlarm(args: JSONObject): String {
        val hour = args.optInt("hour", -1)
        if (hour !in 0..23) return "没听懂要设几点的闹钟"
        val minute = args.optInt("minute", 0).coerceIn(0, 59)
        val label = args.optString("label")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStart(intent)) {
            "已设置闹钟：%02d:%02d".format(hour, minute)
        } else {
            "无法打开闹钟（可能需要在打开 App 时操作）"
        }
    }

    private fun setTimer(args: JSONObject): String {
        val seconds = args.optInt("seconds", -1)
        if (seconds <= 0) return "没听懂要计时多久"
        val label = args.optString("label")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStart(intent)) "已开始计时 ${seconds} 秒" else "无法打开计时器"
    }

    private fun openApp(args: JSONObject): String {
        val query = args.optString("app_name").trim()
        if (query.isBlank()) return "没说要打开哪个应用"
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(query, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(query, true) }
            ?: return "没找到应用「$query」"
        val launch = Intent(main).apply {
            component = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStart(launch)) "已打开「${match.loadLabel(pm)}」" else "无法打开应用"
    }

    private fun openUrl(args: JSONObject): String {
        var url = args.optString("url").trim()
        if (url.isBlank()) return "没说要打开哪个网址"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(intent)) "已打开网址" else "无法打开网址"
    }

    @Suppress("DEPRECATION")
    private fun mediaControl(args: JSONObject): String {
        val action = args.optString("action")
        val keyCode = when (action) {
            "play", "pause", "toggle", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "不支持的媒体操作：$action"
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return "已执行媒体操作：$action"
    }

    private fun setFlashlight(args: JSONObject): String {
        val on = args.optBoolean("on", true)
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "这台设备没有可用的闪光灯"
        cm.setTorchMode(cameraId, on)
        return if (on) "已打开手电筒" else "已关闭手电筒"
    }

    private fun setVolume(args: JSONObject): String {
        val action = args.optString("action")
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action) {
            "up" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
            )

            "down" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
            )

            "mute" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI
            )

            "unmute" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI
            )

            "max" -> am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                AudioManager.FLAG_SHOW_UI
            )

            else -> return "不支持的音量操作：$action"
        }
        return "已调节音量：$action"
    }

    private fun makeCall(args: JSONObject): String {
        val target = args.optString("target").trim()
        if (target.isBlank()) return "没说要打给谁"
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(target)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(intent)) "已打开拨号：$target" else "无法打开拨号界面"
    }

    private fun saveNote(args: JSONObject): String {
        val text = args.optString("text").trim()
        if (text.isBlank()) return "没听到要记的内容"
        notes.add(text)
        return "已记下：$text"
    }

    private fun listNotes(): String {
        val items = notes.list()
        return if (items.isEmpty()) "还没有任何便签" else items.joinToString("；")
    }

    private fun openSettings(args: JSONObject): String {
        val action = when (args.optString("page")) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(intent)) "已打开设置" else "无法打开设置"
    }

    private fun createCalendarEvent(args: JSONObject): String {
        val title = args.optString("title").trim().ifBlank { "语音助手日程" }
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", -1)
        if (hour !in 0..23 || minute !in 0..59) return "没听懂日程时间"
        val dayOffset = args.optInt("day_offset", 0).coerceIn(0, 30)

        val begin = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .apply {
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return if (tryStart(intent)) "已打开日历，创建「$title」" else "无法打开日历"
    }

    private fun vibrate(args: JSONObject): String {
        val ms = args.optInt("ms", 300).coerceIn(50, 2000).toLong()
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator ?: return "这台设备不支持震动"
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        return "已震动"
    }

    // ---- 天气（Open-Meteo，免费无需 Key）----

    private suspend fun getWeather(args: JSONObject): String = withContext(Dispatchers.IO) {
        val city = args.optString("city").trim()
        if (city.isBlank()) return@withContext "没说要查哪个城市"

        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(city, "UTF-8") + "&count=1&language=zh&format=json"
            val geo = JSONObject(httpGet(geoUrl))
            val results = geo.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext "没找到城市「$city」"
            }

            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.optString("name", city)

            val wxUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
            val current = JSONObject(httpGet(wxUrl)).getJSONObject("current")

            val temp = current.optDouble("temperature_2m", Double.NaN)
            val humidity = current.optInt("relative_humidity_2m", -1)
            val wind = current.optDouble("wind_speed_10m", Double.NaN)
            val code = current.optInt("weather_code", -1)

            buildString {
                append("$name 现在${weatherDescription(code)}")
                if (!temp.isNaN()) append("，气温 ${temp}℃")
                if (humidity >= 0) append("，湿度 $humidity%")
                if (!wind.isNaN()) append("，风速 ${wind} km/h")
            }
        } catch (t: Throwable) {
            "查天气失败：${t.message ?: "网络错误"}"
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "VoiceAssistant/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "晴"
        1, 2 -> "少云"
        3 -> "阴"
        45, 48 -> "有雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61, 63, 65 -> "下雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "下雪"
        77 -> "雪粒"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷阵雨伴冰雹"
        else -> "天气未知"
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}
