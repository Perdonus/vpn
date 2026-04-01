package com.white.vpn.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.white.vpn.vpn.VpnManager

class VpnToggleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        VpnToggleWidgetRenderer.update(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        val permissionIntent = VpnManager.toggle(context)
        if (permissionIntent != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.putExtra("request_vpn_permission", true)
                context.startActivity(launchIntent)
            }
        }
        VpnToggleWidgetRenderer.updateAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.white.vpn.widget.action.TOGGLE"
    }
}
