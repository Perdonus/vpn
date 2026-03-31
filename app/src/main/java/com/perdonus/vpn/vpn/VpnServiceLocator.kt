package com.perdonus.vpn.vpn

object VpnServiceLocator {
    @Volatile
    var dependencies: VpnDependencies? = null
}
