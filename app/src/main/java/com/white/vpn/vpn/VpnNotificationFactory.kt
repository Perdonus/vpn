package com.white.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.white.vpn.R

internal object VpnNotificationFactory {
    const val CHANNEL_ID = "white_vpn_channel"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "WhiteVPN status"
            enableLights(false)
            enableVibration(false)
            lightColor = Color.TRANSPARENT
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        state: VpnRuntimeState,
    ): Notification {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: Intent()
        val contentIntent =
            PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            PendingIntent.getService(
                context,
                101,
                Intent(context, PerdonusVpnService::class.java).setAction(PerdonusVpnService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val body = buildBody(context, state)

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_status)
                .setContentTitle(buildTitle(context, state))
                .setSubText(context.getString(R.string.app_name))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(state.isRunning)
                .setShowWhen(state.startedAtEpochMs != null)
                .setWhen(state.startedAtEpochMs ?: System.currentTimeMillis())
                .setUsesChronometer(state.status == TunnelStatus.CONNECTED && state.startedAtEpochMs != null)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)

        if (state.isRunning) {
            builder.addAction(
                R.drawable.ic_stop,
                context.getString(R.string.notification_stop),
                stopIntent,
            )
        }

        return builder.build()
    }

    private fun buildTitle(
        context: Context,
        state: VpnRuntimeState,
    ): String =
        when (state.status) {
            TunnelStatus.CONNECTING -> context.getString(R.string.notification_connecting)
            TunnelStatus.STOPPING -> context.getString(R.string.notification_stopping)
            TunnelStatus.PERMISSION_REQUIRED -> context.getString(R.string.notification_permission_required)
            TunnelStatus.CONNECTED -> state.activeProfileName ?: context.getString(R.string.app_name)
            TunnelStatus.ERROR -> state.message ?: context.getString(R.string.status_error_generic)
            TunnelStatus.IDLE -> context.getString(R.string.app_name)
        }

    private fun buildBody(
        context: Context,
        state: VpnRuntimeState,
    ): String =
        when (state.status) {
            TunnelStatus.CONNECTED -> {
                val parts = mutableListOf<String>()
                parts += context.getString(R.string.notification_connected)
                state.activePingMs?.let { parts += context.getString(R.string.status_ping_value, it) }
                state.startedAtEpochMs?.let(::formatUptime)?.let(parts::add)
                parts.joinToString(separator = "  •  ")
            }

            TunnelStatus.CONNECTING -> context.getString(R.string.notification_connecting)
            TunnelStatus.STOPPING -> context.getString(R.string.notification_stopping)
            TunnelStatus.PERMISSION_REQUIRED -> context.getString(R.string.notification_permission_required)
            TunnelStatus.ERROR -> state.message ?: context.getString(R.string.status_error_generic)
            TunnelStatus.IDLE -> state.message ?: context.getString(R.string.app_name)
        }

    private fun formatUptime(startedAtEpochMs: Long): String {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1000L).coerceAtLeast(0)
        val hours = elapsedSeconds / 3600L
        val minutes = (elapsedSeconds % 3600L) / 60L
        val seconds = elapsedSeconds % 60L
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
