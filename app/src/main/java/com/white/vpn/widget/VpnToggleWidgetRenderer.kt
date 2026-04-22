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
    }

    fun updateCompact(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        manager.updateAppWidgets(appWidgetIds, buildRemoteViews(context))
    }

    private fun updateCompactAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, VpnToggleWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            updateCompact(context, manager, ids)
        }
    }

    private fun AppWidgetManager.updateAppWidgets(appWidgetIds: IntArray, remoteViews: RemoteViews) {
        appWidgetIds.forEach { id ->
            updateAppWidget(id, remoteViews)
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val state = VpnManager.stateSnapshot()
        val presentation = buildPresentation(context, state)
        val pendingIntent = buildTogglePendingIntent(context)

        return RemoteViews(context.packageName, presentation.layoutRes).apply {
            setTextViewText(presentation.titleViewId, presentation.title)
            setTextViewText(presentation.detailViewId, presentation.detail)
            setViewVisibility(presentation.titleViewId, presentation.titleVisibility)
            setInt(presentation.rootViewId, "setBackgroundResource", presentation.backgroundRes)
            setInt(presentation.titleViewId, "setTextColor", Color.WHITE)
            setInt(presentation.detailViewId, "setTextColor", Color.WHITE)
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
    ): WidgetPresentation {
        val isConnected = state.status == TunnelStatus.CONNECTED
        val isBusy = state.status == TunnelStatus.CONNECTING || state.status == TunnelStatus.STOPPING
        val background =
            when (state.status) {
                TunnelStatus.CONNECTED -> R.drawable.widget_toggle_background_active
                TunnelStatus.CONNECTING, TunnelStatus.STOPPING, TunnelStatus.PERMISSION_REQUIRED, TunnelStatus.ERROR ->
                    R.drawable.widget_toggle_background_pending
                TunnelStatus.IDLE -> R.drawable.widget_toggle_background
            }

        val iconRes =
            when {
                isBusy -> R.drawable.ic_refresh
                isConnected -> R.drawable.ic_signal
                else -> R.drawable.ic_power
            }

        return WidgetPresentation(
            layoutRes = R.layout.widget_vpn_toggle,
            rootViewId = R.id.widget_container,
            titleViewId = R.id.widget_title,
            detailViewId = R.id.widget_state,
            iconViewId = R.id.widget_icon,
            title = context.getString(R.string.app_name),
            detail = buildStateLabel(context, state),
            titleVisibility = android.view.View.GONE,
            backgroundRes = background,
            iconRes = iconRes,
            clickTargets = listOf(R.id.widget_icon, R.id.widget_state),
        )
    }

    private fun buildStateLabel(
        context: Context,
        state: VpnRuntimeState,
    ): String =
        when (state.status) {
            TunnelStatus.CONNECTED -> context.getString(R.string.widget_state_on)
            TunnelStatus.CONNECTING -> context.getString(R.string.widget_state_connecting)
            TunnelStatus.STOPPING -> context.getString(R.string.widget_state_stopping)
            TunnelStatus.PERMISSION_REQUIRED -> context.getString(R.string.status_permission_required)
            TunnelStatus.ERROR -> context.getString(R.string.status_error_generic)
            TunnelStatus.IDLE -> context.getString(R.string.widget_state_off)
        }

    private data class WidgetPresentation(
        val layoutRes: Int,
        val rootViewId: Int,
        val titleViewId: Int,
        val detailViewId: Int,
        val iconViewId: Int?,
        val title: String,
        val detail: String,
        val titleVisibility: Int,
        val backgroundRes: Int,
        val iconRes: Int,
        val clickTargets: List<Int>,
    )
}
