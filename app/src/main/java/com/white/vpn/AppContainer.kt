package com.white.vpn

import android.content.Context
import com.white.vpn.data.AppSettingsStore
import com.white.vpn.data.ServerRepository
import com.white.vpn.integration.AppVpnDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = AppSettingsStore(context.applicationContext)
    val serverRepository = ServerRepository(settingsStore)
    val vpnDependencies = AppVpnDependencies(serverRepository, applicationScope)
}
