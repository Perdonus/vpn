package com.perdonus.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

internal object VpnNotificationFactory {
    const val CHANNEL_ID = "perdonus_vpn_channel"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Perdonus VPN status"
            enableLights(false)
            enableVibration(false)
            lightColor = Color.TRANSPARENT
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        state: VpnRuntimeState,
    ): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val contentIntent = PendingIntent.getActivity(
            context,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            context,
            101,
            Intent(context, PerdonusVpnService::class.java).setAction(PerdonusVpnService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = buildString {
            state.activePingMs?.let { append("Ping: ${it} ms") }
            val uptime = state.startedAtEpochMs?.let(::formatUptime)
            if (!uptime.isNullOrBlank()) {
                if (isNotEmpty()) append("  •  ")
                append(uptime)
            }
            if (isEmpty()) {
                append(state.message ?: "VPN active")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(state.activeProfileName ?: "Perdonus VPN")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(state.isRunning)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Остановить",
                stopIntent,
            )
            .build()
    }

    private fun formatUptime(startedAtEpochMs: Long): String {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1000L).coerceAtLeast(0)
        val hours = elapsedSeconds / 3600L
        val minutes = (elapsedSeconds % 3600L) / 60L
        val seconds = elapsedSeconds % 60L
        return String.format("Up: %02d:%02d:%02d", hours, minutes, seconds)
    }
}
