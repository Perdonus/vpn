package com.perdonus.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.perdonus.vpn.ui.main.MainScreen
import com.perdonus.vpn.ui.main.MainViewModel
import com.perdonus.vpn.ui.theme.PerdonusVpnTheme
import com.perdonus.vpn.vpn.VpnManager

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        val container = (application as VpnApplication).appContainer
        viewModelFactory {
            initializer {
                MainViewModel(container.serverRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionOnStart = intent.getBooleanExtra(EXTRA_REQUEST_VPN_PERMISSION, false)
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (VpnService.prepare(this) == null) {
                    VpnManager.requestStart(this, viewModel.uiState.value.manualRequestedProfileId)
                }
            }

        setContent {
            PerdonusVpnTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val shouldRequestPermission = remember(requestPermissionOnStart) { requestPermissionOnStart }

                LaunchedEffect(shouldRequestPermission) {
                    if (!shouldRequestPermission) return@LaunchedEffect
                    VpnService.prepare(context)?.let(permissionLauncher::launch)
                }

                MainScreen(
                    state = uiState,
                    onToggleConnection = {
                        val permissionIntent = VpnManager.toggle(context, uiState.manualRequestedProfileId)
                        if (permissionIntent != null) {
                            permissionLauncher.launch(permissionIntent)
                        }
                    },
                    onRefreshSubscription = viewModel::refreshSubscription,
                    onSelectServer = viewModel::selectServer,
                    onSaveSubscriptionUrl = viewModel::saveSubscriptionUrl,
                    onDismissMessage = viewModel::dismissMessage,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_REQUEST_VPN_PERMISSION = "request_vpn_permission"
    }
}

