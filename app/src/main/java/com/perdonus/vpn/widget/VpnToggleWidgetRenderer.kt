package com.perdonus.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.perdonus.vpn.vpn.VpnManager

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
        val running = VpnManager.stateSnapshot().isRunning
        val label = if (running) "VPN ON" else "VPN OFF"
        val background = if (running) Color.parseColor("#0E6F55") else Color.parseColor("#2C3E50")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            301,
            Intent(context, VpnToggleWidgetProvider::class.java).setAction(VpnToggleWidgetProvider.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteViews = RemoteViews("android", android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
            setInt(android.R.id.text1, "setTextColor", Color.WHITE)
            setInt(android.R.id.text1, "setBackgroundColor", background)
            setOnClickPendingIntent(android.R.id.text1, pendingIntent)
        }

        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, remoteViews)
        }
    }
}
