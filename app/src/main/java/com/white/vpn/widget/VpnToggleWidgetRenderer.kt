package com.white.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.white.vpn.R
import com.white.vpn.vpn.TunnelStatus
import com.white.vpn.vpn.VpnManager
import com.white.vpn.vpn.VpnRuntimeState

internal object VpnToggleWidgetRenderer {
    fun updateAll(context: Context) {
        updateCompactAll(context)
        updateInfoAll(context)
    }

    fun updateCompact(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        manager.updateAppWidgets(appWidgetIds, buildRemoteViews(context, WidgetKind.COMPACT))
    }

    fun updateInfo(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        manager.updateAppWidgets(appWidgetIds, buildRemoteViews(context, WidgetKind.INFO))
    }

    private fun updateCompactAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, VpnToggleWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            updateCompact(context, manager, ids)
        }
    }

    private fun updateInfoAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, VpnInfoWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            updateInfo(context, manager, ids)
        }
    }

    private fun AppWidgetManager.updateAppWidgets(appWidgetIds: IntArray, remoteViews: RemoteViews) {
        appWidgetIds.forEach { id ->
            updateAppWidget(id, remoteViews)
        }
    }

    private fun buildRemoteViews(
        context: Context,
        kind: WidgetKind,
    ): RemoteViews {
        val state = VpnManager.stateSnapshot()
        val presentation = buildPresentation(context, state, kind)
        val pendingIntent = buildTogglePendingIntent(context)

        return RemoteViews(context.packageName, presentation.layoutRes).apply {
            setTextViewText(presentation.titleViewId, presentation.title)
            setTextViewText(presentation.detailViewId, presentation.detail)
            presentation.secondaryTextViewId?.let { id ->
                setTextViewText(id, presentation.secondary)
            }
            presentation.tertiaryTextViewId?.let { id ->
                setTextViewText(id, presentation.tertiary)
            }
            setInt(presentation.rootViewId, "setBackgroundResource", presentation.backgroundRes)
            setInt(presentation.titleViewId, "setTextColor", Color.WHITE)
            setInt(presentation.detailViewId, "setTextColor", Color.WHITE)
            presentation.secondaryTextViewId?.let { setInt(it, "setTextColor", Color.WHITE) }
            presentation.tertiaryTextViewId?.let { setInt(it, "setTextColor", Color.WHITE) }
            presentation.iconViewId?.let { setImageViewResource(it, presentation.iconRes) }
            setOnClickPendingIntent(presentation.rootViewId, pendingIntent)
            presentation.clickTargets.forEach { setOnClickPendingIntent(it, pendingIntent) }
        }
    }

    private fun buildTogglePendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, VpnToggleWidgetProvider::class.java).setAction(VpnToggleWidgetProvider.ACTION_TOGGLE)
        return PendingIntent.getBroadcast(
            context,
            301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildPresentation(
        context: Context,
        state: VpnRuntimeState,
        kind: WidgetKind,
    ): WidgetPresentation {
        val isConnected = state.status == TunnelStatus.CONNECTED
        val isBusy = state.status == TunnelStatus.CONNECTING || state.status == TunnelStatus.STOPPING
        val background =
            when (state.status) {
                TunnelStatus.CONNECTED -> when (kind) {
                    WidgetKind.COMPACT -> R.drawable.widget_toggle_background_active
                    WidgetKind.INFO -> R.drawable.widget_info_background_active
                }
                TunnelStatus.CONNECTING, TunnelStatus.STOPPING, TunnelStatus.PERMISSION_REQUIRED, TunnelStatus.ERROR ->
                    when (kind) {
                        WidgetKind.COMPACT -> R.drawable.widget_toggle_background_pending
                        WidgetKind.INFO -> R.drawable.widget_info_background_pending
                    }
                TunnelStatus.IDLE ->
                    when (kind) {
                        WidgetKind.COMPACT -> R.drawable.widget_toggle_background
                        WidgetKind.INFO -> R.drawable.widget_info_background
                    }
            }

        val iconRes =
            when {
                isBusy -> R.drawable.ic_refresh
                isConnected -> R.drawable.ic_signal
                else -> R.drawable.ic_power
            }

        return when (kind) {
            WidgetKind.COMPACT -> buildCompactPresentation(context, state, background, iconRes)
            WidgetKind.INFO -> buildInfoPresentation(context, state, background, iconRes)
        }
    }

    private fun buildCompactPresentation(
        context: Context,
        state: VpnRuntimeState,
        background: Int,
        iconRes: Int,
    ): WidgetPresentation {
        val title =
            when (state.status) {
                TunnelStatus.CONNECTED -> state.activeProfileName ?: context.getString(R.string.app_name)
                TunnelStatus.IDLE -> context.getString(R.string.app_name)
                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING,
                TunnelStatus.PERMISSION_REQUIRED,
                TunnelStatus.ERROR ->
                    context.getString(R.string.app_name)
            }
        val detail =
            when (state.status) {
                TunnelStatus.CONNECTED -> {
                    state.activePingMs?.let { context.getString(R.string.status_ping_value, it) }
                        ?: context.getString(R.string.status_ping_unknown)
                }
                TunnelStatus.CONNECTING -> state.message ?: context.getString(R.string.widget_state_selecting_server)
                TunnelStatus.STOPPING -> state.message ?: context.getString(R.string.widget_state_stopping)
                TunnelStatus.PERMISSION_REQUIRED -> state.message ?: context.getString(R.string.status_permission_required)
                TunnelStatus.ERROR -> state.message ?: context.getString(R.string.status_error_generic)
                TunnelStatus.IDLE -> context.getString(R.string.widget_state_off)
            }
        val secondary =
            if (state.status == TunnelStatus.CONNECTED) {
                if (state.isAutoMode) context.getString(R.string.widget_mode_auto) else context.getString(R.string.widget_mode_manual)
            } else {
                context.getString(R.string.widget_tap_to_toggle)
            }
        return WidgetPresentation(
            layoutRes = R.layout.widget_vpn_toggle,
            rootViewId = R.id.widget_container,
            titleViewId = R.id.widget_title,
            detailViewId = R.id.widget_state,
            secondaryTextViewId = R.id.widget_mode,
            tertiaryTextViewId = null,
            iconViewId = R.id.widget_icon,
            title = title,
            detail = detail,
            secondary = secondary,
            tertiary = "",
            backgroundRes = background,
            iconRes = iconRes,
            clickTargets = listOf(R.id.widget_icon, R.id.widget_title, R.id.widget_state, R.id.widget_mode),
        )
    }

    private fun buildInfoPresentation(
        context: Context,
        state: VpnRuntimeState,
        background: Int,
        iconRes: Int,
    ): WidgetPresentation {
        val title =
            when (state.status) {
                TunnelStatus.CONNECTED -> state.activeProfileName ?: context.getString(R.string.app_name)
                TunnelStatus.IDLE -> context.getString(R.string.app_name)
                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING,
                TunnelStatus.PERMISSION_REQUIRED,
                TunnelStatus.ERROR ->
                    context.getString(R.string.app_name)
            }
        val detail =
            when (state.status) {
                TunnelStatus.CONNECTED -> {
                    state.activePingMs?.let { context.getString(R.string.status_ping_value, it) }
                        ?: context.getString(R.string.status_ping_unknown)
                }
                TunnelStatus.CONNECTING -> state.message ?: context.getString(R.string.widget_state_selecting_server)
                TunnelStatus.STOPPING -> state.message ?: context.getString(R.string.widget_state_stopping)
                TunnelStatus.PERMISSION_REQUIRED -> state.message ?: context.getString(R.string.status_permission_required)
                TunnelStatus.ERROR -> state.message ?: context.getString(R.string.status_error_generic)
                TunnelStatus.IDLE -> context.getString(R.string.widget_state_off)
            }
        val secondary =
            if (state.isAutoMode) context.getString(R.string.widget_mode_auto) else context.getString(R.string.widget_mode_manual)
        val tertiary =
            when (state.status) {
                TunnelStatus.CONNECTED -> buildConnectedInfoLine(context, state)
                TunnelStatus.CONNECTING -> context.getString(R.string.widget_state_selecting_server)
                TunnelStatus.STOPPING -> context.getString(R.string.widget_state_stopping)
                TunnelStatus.PERMISSION_REQUIRED -> context.getString(R.string.status_permission_required)
                TunnelStatus.ERROR -> context.getString(R.string.status_error_generic)
                TunnelStatus.IDLE -> context.getString(R.string.widget_tap_to_toggle)
            }
        return WidgetPresentation(
            layoutRes = R.layout.widget_vpn_info,
            rootViewId = R.id.widget_info_container,
            titleViewId = R.id.widget_info_title,
            detailViewId = R.id.widget_info_detail,
            secondaryTextViewId = R.id.widget_info_mode,
            tertiaryTextViewId = R.id.widget_info_hint,
            iconViewId = R.id.widget_info_icon,
            title = title,
            detail = detail,
            secondary = secondary,
            tertiary = tertiary,
            backgroundRes = background,
            iconRes = iconRes,
            clickTargets = listOf(R.id.widget_info_icon, R.id.widget_info_title, R.id.widget_info_detail, R.id.widget_info_mode, R.id.widget_info_hint),
        )
    }

    private fun buildConnectedInfoLine(
        context: Context,
        state: VpnRuntimeState,
    ): String {
        val uptime = state.startedAtEpochMs?.let(::formatUptime).orEmpty()
        val received = context.getString(R.string.traffic_received, formatBytes(state.sessionRxBytes))
        val sent = context.getString(R.string.traffic_sent, formatBytes(state.sessionTxBytes))
        return listOfNotNull(
            uptime.takeIf { it.isNotBlank() },
            "$received $sent",
        ).joinToString(separator = " • ")
    }

    private fun formatUptime(startedAtEpochMs: Long): String {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1_000L).coerceAtLeast(0L)
        val hours = elapsedSeconds / 3_600L
        val minutes = (elapsedSeconds % 3_600L) / 60L
        val seconds = elapsedSeconds % 60L
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        val units = arrayOf("B", "KB", "MB", "GB")
        var unitIndex = 0
        var value = safeBytes.toDouble()
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        val formatted =
            if (unitIndex == 0) {
                value.toLong().toString()
            } else {
                String.format("%.1f", value)
            }
        return "$formatted ${units[unitIndex]}"
    }

    private enum class WidgetKind {
        COMPACT,
        INFO,
    }

    private data class WidgetPresentation(
        val layoutRes: Int,
        val rootViewId: Int,
        val titleViewId: Int,
        val detailViewId: Int,
        val secondaryTextViewId: Int?,
        val tertiaryTextViewId: Int?,
        val iconViewId: Int?,
        val title: String,
        val detail: String,
        val secondary: String,
        val tertiary: String,
        val backgroundRes: Int,
        val iconRes: Int,
        val clickTargets: List<Int>,
    )
}
