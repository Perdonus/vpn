package com.white.vpn.widget

import android.content.ComponentName
import android.content.Intent
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.white.vpn.R
import com.white.vpn.vpn.TunnelStatus
import com.white.vpn.vpn.VpnManager

class WhiteVpnTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val state = VpnManager.stateSnapshot()
        if (state.isRunning) {
            VpnManager.stop(this)
            updateTile()
            return
        }

        if (WidgetActionHelper.needsNotificationPermission(this) || VpnService.prepare(this) != null) {
            launchActivityAndCollapse()
            updateTile()
            return
        }

        VpnManager.requestStart(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val state = VpnManager.stateSnapshot()

        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = buildSubtitle(state)
        }
        tile.icon =
            Icon.createWithResource(
                this,
                when (state.status) {
                    TunnelStatus.CONNECTED -> R.drawable.ic_signal
                    TunnelStatus.CONNECTING, TunnelStatus.STOPPING -> R.drawable.ic_refresh
                    TunnelStatus.PERMISSION_REQUIRED, TunnelStatus.ERROR, TunnelStatus.IDLE -> R.drawable.ic_power
                },
            )
        tile.state =
            when (state.status) {
                TunnelStatus.CONNECTED, TunnelStatus.CONNECTING -> Tile.STATE_ACTIVE
                TunnelStatus.PERMISSION_REQUIRED, TunnelStatus.ERROR, TunnelStatus.IDLE, TunnelStatus.STOPPING -> Tile.STATE_INACTIVE
            }
        tile.updateTile()
    }

    private fun buildSubtitle(state: com.white.vpn.vpn.VpnRuntimeState): String =
        when (state.status) {
            TunnelStatus.CONNECTED -> {
                val ping = state.activePingMs?.let { getString(R.string.status_ping_value, it) }
                    ?: getString(R.string.status_ping_unknown)
                val profile = state.activeProfileName?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.app_name)
                "$profile | $ping"
            }

            TunnelStatus.CONNECTING -> state.message ?: getString(R.string.status_connecting)
            TunnelStatus.STOPPING -> state.message ?: getString(R.string.status_stopping)
            TunnelStatus.PERMISSION_REQUIRED -> state.message ?: getString(R.string.status_permission_required)
            TunnelStatus.ERROR -> state.message ?: getString(R.string.status_error_generic)
            TunnelStatus.IDLE -> getString(R.string.widget_state_off)
        }

    private fun launchActivityAndCollapse() {
        val intent =
            Intent(this, com.white.vpn.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(com.white.vpn.MainActivity.EXTRA_REQUEST_VPN_PERMISSION, true)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    91,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestRefresh(context: android.content.Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, WhiteVpnTileService::class.java),
            )
        }
    }
}
