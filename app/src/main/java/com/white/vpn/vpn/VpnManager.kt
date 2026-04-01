package com.white.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.white.vpn.widget.VpnToggleWidgetRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnManager {
    private val _state = MutableStateFlow(VpnRuntimeState())
    val state: StateFlow<VpnRuntimeState> = _state.asStateFlow()

    fun stateSnapshot(): VpnRuntimeState = _state.value

    fun requestStart(context: Context, requestedProfileId: String? = null): Intent? {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            publish(
                VpnRuntimeState(
                    status = TunnelStatus.PERMISSION_REQUIRED,
                    permissionIntent = prepareIntent,
                    message = "VPN permission required",
                ),
                context,
            )
            return prepareIntent
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, PerdonusVpnService::class.java).apply {
                action = PerdonusVpnService.ACTION_START
                putExtra(PerdonusVpnService.EXTRA_PROFILE_ID, requestedProfileId)
            },
        )
        return null
    }

    fun stop(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PerdonusVpnService::class.java).setAction(PerdonusVpnService.ACTION_STOP),
        )
    }

    fun toggle(context: Context, requestedProfileId: String? = null): Intent? {
        return if (_state.value.isRunning) {
            stop(context)
            null
        } else {
            requestStart(context, requestedProfileId)
        }
    }

    internal fun publish(state: VpnRuntimeState, context: Context? = null) {
        _state.value = state
        if (context != null) {
            VpnToggleWidgetRenderer.updateAll(context)
        }
    }
}
