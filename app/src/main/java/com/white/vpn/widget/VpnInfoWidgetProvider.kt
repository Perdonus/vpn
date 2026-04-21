package com.white.vpn.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.white.vpn.vpn.VpnManager

class VpnInfoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        VpnToggleWidgetRenderer.updateInfo(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        if (!VpnManager.stateSnapshot().isRunning && WidgetActionHelper.needsNotificationPermission(context)) {
            WidgetActionHelper.launchPermissionFlow(context)
            VpnToggleWidgetRenderer.updateAll(context)
            return
        }

        val permissionIntent = VpnManager.toggle(context)
        if (permissionIntent != null) {
            WidgetActionHelper.launchPermissionFlow(context)
        }
        VpnToggleWidgetRenderer.updateAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = VpnToggleWidgetProvider.ACTION_TOGGLE
    }
}
