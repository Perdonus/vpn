package com.perdonus.vpn

import android.content.Context
import com.perdonus.vpn.data.AppSettingsStore
import com.perdonus.vpn.data.ServerRepository
import com.perdonus.vpn.integration.AppVpnDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = AppSettingsStore(context.applicationContext)
    val serverRepository = ServerRepository(settingsStore)
    val vpnDependencies = AppVpnDependencies(serverRepository, applicationScope)
}
