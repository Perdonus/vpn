package com.white.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.white.vpn.ui.main.MainUiState
import com.white.vpn.ui.main.MainScreen
import com.white.vpn.ui.main.MainViewModel
import com.white.vpn.ui.theme.WhiteVpnTheme
import com.white.vpn.vpn.TunnelStatus
import com.white.vpn.vpn.VpnManager
import com.white.vpn.vpn.VpnRuntimeState

class MainActivity : ComponentActivity() {
    private var pendingProfileId: String? = null
    private var pendingToggleAfterNotificationPermission = false

    private val viewModel by viewModels<MainViewModel> {
        val container = (application as VpnApplication).appContainer
        viewModelFactory {
            initializer {
                MainViewModel(
                    serverRepository = container.serverRepository,
                    installedAppsRepository = container.installedAppsRepository,
                )
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingToggleAfterNotificationPermission) {
                val profileId = pendingProfileId
                pendingToggleAfterNotificationPermission = false
                pendingProfileId = null
                continueConnectionFlow(profileId)
            } else if (pendingToggleAfterNotificationPermission) {
                pendingToggleAfterNotificationPermission = false
                pendingProfileId = null
                VpnManager.publish(
                    VpnRuntimeState(
                        status = TunnelStatus.PERMISSION_REQUIRED,
                        message = getString(R.string.status_notification_permission_required),
                    ),
                    this,
                )
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (VpnService.prepare(this) == null) {
                VpnManager.requestStart(this, pendingProfileId)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionOnStart = intent.getBooleanExtra(EXTRA_REQUEST_VPN_PERMISSION, false)

        setContent {
            WhiteVpnTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(requestPermissionOnStart) {
                    if (!requestPermissionOnStart) return@LaunchedEffect
                    beginConnectionFlow(uiState)
                }

                MainScreen(
                    state = uiState,
                    onToggleConnection = {
                        beginConnectionFlow(uiState)
                    },
                    onOpenChannel = {
                        viewModel.dismissChannelPrompt()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL)))
                    },
                    onSelectSubscriptionMode = { mode ->
                        viewModel.switchSubscriptionMode(mode)
                    },
                    onOpenSplitTunnel = {
                        viewModel.dismissMessage()
                    },
                    onSelectSplitTunnelMode = { mode ->
                        viewModel.setSplitTunnelMode(mode)
                    },
                    onToggleSplitTunnelPackage = { packageName ->
                        viewModel.toggleSplitTunnelPackage(packageName)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_VPN_PERMISSION, false)) {
            beginConnectionFlow(viewModel.uiState.value)
        }
    }

    private fun beginConnectionFlow(uiState: MainUiState) {
        pendingProfileId = uiState.manualRequestedProfileId
        if (uiState.connection.isRunning) {
            continueConnectionFlow(uiState.manualRequestedProfileId)
            return
        }
        if (needsNotificationPermission()) {
            pendingToggleAfterNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueConnectionFlow(uiState.manualRequestedProfileId)
    }

    private fun continueConnectionFlow(profileId: String?) {
        pendingProfileId = profileId
        val permissionIntent = VpnManager.toggle(this, profileId)
        if (permissionIntent != null) {
            permissionLauncher.launch(permissionIntent)
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_REQUEST_VPN_PERMISSION = "request_vpn_permission"
        private const val CHANNEL_URL = "https://t.me/plugin_ai"
    }
}
