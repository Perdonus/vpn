package com.white.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.white.vpn.R
import com.white.vpn.widget.WhiteVpnTileService
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
                    message = context.getString(R.string.status_permission_required),
                ),
                context,
            )
            return prepareIntent
        }
        publish(
            _state.value.copy(
                status = TunnelStatus.CONNECTING,
                message = context.getString(R.string.status_connecting),
                permissionIntent = null,
            ),
            context,
        )
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
        publish(
            _state.value.copy(
                status = TunnelStatus.STOPPING,
                message = context.getString(R.string.status_stopping),
                permissionIntent = null,
            ),
            context,
        )
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

    internal fun publish(
        state: VpnRuntimeState,
        context: Context? = null,
        updateWidgets: Boolean = true,
    ) {
        val previousState = _state.value
        _state.value = state
        if (context != null && updateWidgets && shouldRefreshWidget(previousState, state)) {
            WhiteVpnTileService.requestRefresh(context)
        }
    }

    private fun shouldRefreshWidget(
        previousState: VpnRuntimeState,
        newState: VpnRuntimeState,
    ): Boolean =
        previousState.status != newState.status ||
            previousState.activeProfileId != newState.activeProfileId ||
            previousState.activeProfileName != newState.activeProfileName ||
            previousState.activePingMs != newState.activePingMs ||
            previousState.startedAtEpochMs != newState.startedAtEpochMs ||
            previousState.sessionRxBytes != newState.sessionRxBytes ||
            previousState.sessionTxBytes != newState.sessionTxBytes ||
            previousState.isAutoMode != newState.isAutoMode ||
            previousState.message != newState.message ||
            previousState.permissionIntent != newState.permissionIntent
}
