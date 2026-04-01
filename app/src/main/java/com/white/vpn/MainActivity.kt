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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.white.vpn.ui.main.MainScreen
import com.white.vpn.ui.main.MainViewModel
import com.white.vpn.ui.theme.WhiteVpnTheme
import com.white.vpn.vpn.VpnManager

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
        val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (VpnService.prepare(this) == null) {
                    VpnManager.requestStart(this, viewModel.uiState.value.manualRequestedProfileId)
                }
            }
        val requestNotificationPermissionIfNeeded = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            WhiteVpnTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    requestNotificationPermissionIfNeeded()
                }

                LaunchedEffect(requestPermissionOnStart) {
                    if (!requestPermissionOnStart) return@LaunchedEffect
                    VpnService.prepare(context)?.let(permissionLauncher::launch)
                }

                MainScreen(
                    state = uiState,
                    onToggleConnection = {
                        requestNotificationPermissionIfNeeded()
                        val permissionIntent = VpnManager.toggle(context, uiState.manualRequestedProfileId)
                        if (permissionIntent != null) {
                            permissionLauncher.launch(permissionIntent)
                        }
                    },
                    onOpenChannel = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL)))
                    },
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
        private const val CHANNEL_URL = "https://t.me/plugin_ai"
    }
}
