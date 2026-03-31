package com.perdonus.vpn.domain

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val subscriptionUrl: String,
    val selectedServerId: String = VpnServer.AUTO_ID,
    val servers: List<VpnServer> = emptyList(),
    val lastSubscriptionSyncEpochMs: Long? = null,
) {
    val serversWithAuto: List<VpnServer>
        get() = listOf(VpnServer.auto()) + servers
}
