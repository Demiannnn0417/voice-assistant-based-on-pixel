package com.demian.voiceassistant.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.view.KeyEvent
import com.demian.voiceassistant.data.NotesStore
import org.json.JSONObject

/**
 * 设备动作执行器：把大模型选择的「工具」翻译成真实的系统操作。
 *
 * 说明：带界面的操作（闹钟/打开应用/拨号）在**后台**可能被 Android 限制，
 * 这里统一 try/catch 并在失败时返回说明，避免崩溃。
 */
class DeviceActions(
    private val context: Context,
    private val notes: NotesStore,
) {

    fun execute(name: String, args: JSONObject): String = try {
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

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}
