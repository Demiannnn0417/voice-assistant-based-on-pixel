package com.demian.voiceassistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.demian.voiceassistant.MainActivity
import com.demian.voiceassistant.R
import com.demian.voiceassistant.data.SettingsStore
import com.demian.voiceassistant.diag.Diag

/**
 * 开机后尝试恢复常驻唤醒。
 *
 * Android 14+ 对「从后台启动麦克风类型前台服务」有限制，启动可能失败；
 * 失败时退化为一条通知，让用户点开 App 恢复。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = SettingsStore(context)
        if (!settings.wakeEnabled) {
            Diag.log("boot", "wake not enabled, skip")
            return
        }

        Diag.log("boot", "trying to restore wake")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java)
                    .setAction(WakeWordService.ACTION_START_WAKE)
            )
        } catch (t: Throwable) {
            Diag.log("boot", "restore failed: ${t.message ?: t.javaClass.simpleName}")
            postRestoreNotification(context)
        }
    }

    private fun postRestoreNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "开机恢复", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("语音助手")
            .setContentText("点击打开并恢复常驻唤醒")
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // 没有通知权限时忽略
        }
    }

    companion object {
        private const val CHANNEL_ID = "boot_restore"
        private const val NOTIFICATION_ID = 1002
    }
}
