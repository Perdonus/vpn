package com.white.vpn.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.white.vpn.MainActivity
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

        if (!VpnManager.stateSnapshot().isRunning && needsNotificationPermission(context)) {
            launchAppForPermission(context)
            VpnToggleWidgetRenderer.updateAll(context)
            return
        }

        val permissionIntent = VpnManager.toggle(context)
        if (permissionIntent != null) {
            launchAppForPermission(context)
        }
        VpnToggleWidgetRenderer.updateAll(context)
    }

    private fun launchAppForPermission(context: Context) {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_REQUEST_VPN_PERMISSION, true)
            }
        context.startActivity(launchIntent)
    }

    private fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_TOGGLE = "com.white.vpn.widget.action.TOGGLE"
    }
}
