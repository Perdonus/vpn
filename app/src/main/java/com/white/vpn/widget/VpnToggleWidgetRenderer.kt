package com.white.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.white.vpn.R
import com.white.vpn.vpn.VpnManager
import com.white.vpn.vpn.TunnelStatus

internal object VpnToggleWidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, VpnToggleWidgetProvider::class.java))
        if (ids.isEmpty()) return
        update(context, manager, ids)
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = VpnManager.stateSnapshot()
        val (label, background) =
            when (state.status) {
                TunnelStatus.CONNECTED ->
                    (state.activePingMs?.let { "$it ms" } ?: context.getString(R.string.widget_state_on)) to
                        R.drawable.widget_toggle_background_active
                TunnelStatus.CONNECTING ->
                    context.getString(R.string.widget_state_connecting) to R.drawable.widget_toggle_background_pending
                TunnelStatus.STOPPING ->
                    context.getString(R.string.widget_state_stopping) to R.drawable.widget_toggle_background_pending
                else ->
                    context.getString(R.string.widget_state_off) to R.drawable.widget_toggle_background
            }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            301,
            Intent(context, VpnToggleWidgetProvider::class.java).setAction(VpnToggleWidgetProvider.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteViews = RemoteViews(context.packageName, R.layout.widget_vpn_toggle).apply {
            setTextViewText(R.id.widget_state, label)
            setInt(R.id.widget_state, "setTextColor", Color.WHITE)
            setInt(R.id.widget_container, "setBackgroundResource", background)
            setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
            setOnClickPendingIntent(R.id.widget_state, pendingIntent)
        }

        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, remoteViews)
        }
    }
}
