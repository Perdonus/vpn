package com.white.vpn.domain

import com.white.vpn.data.SubscriptionMode
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val subscriptionUrl: String,
    val subscriptionMode: SubscriptionMode = SubscriptionMode.MOBILE,
    val selectedServerId: String = VpnServer.AUTO_ID,
    val servers: List<VpnServer> = emptyList(),
    val lastSubscriptionSyncEpochMs: Long? = null,
) {
    val serversWithAuto: List<VpnServer>
        get() = listOf(VpnServer.auto()) + servers
}
