package com.demian.voiceassistant.phone

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 读取手机本地信息（时间、日期、电量等）。
 *
 * 这些都是**不需要额外权限**的公开系统信息，用 Context 就能拿到。
 */
class PhoneInfoProvider(private val context: Context) : PhoneInfo {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH点mm分", Locale.CHINA)

    /** 例如：现在 14点05分 */
    override fun time(): String {
        val now = LocalTime.now()
        return "现在是 ${now.format(timeFormatter)}"
    }

    /** 例如：今天是 2026年10月10日 星期六 */
    override fun date(): String {
        val today = LocalDate.now()
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
        return "今天是 ${today.year}年${today.monthValue}月${today.dayOfMonth}日 $weekday"
    }

    /** 例如：当前电量 78% */
    override fun battery(): String {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return "暂时读不到电量"
        val percent = (level * 100 / scale)
        val charging = intent != null && isCharging(intent)
        return "当前电量 $percent%" + if (charging) "，正在充电" else ""
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
