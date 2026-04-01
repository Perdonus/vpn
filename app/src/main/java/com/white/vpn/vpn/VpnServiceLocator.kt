package com.white.vpn.vpn

object VpnServiceLocator {
    @Volatile
    var dependencies: VpnDependencies? = null
}
