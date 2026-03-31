package com.perdonus.vpn

import android.app.Application
import com.perdonus.vpn.vpn.VpnServiceLocator

class VpnApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        VpnServiceLocator.dependencies = appContainer.vpnDependencies
    }
}

