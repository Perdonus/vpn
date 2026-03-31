package com.perdonus.vpn.domain

import kotlinx.serialization.Serializable

@Serializable
data class VpnServer(
    val id: String,
    val protocol: ProxyProtocol,
    val displayName: String,
    val host: String,
    val port: Int,
    val rawLink: String,
    val user: String? = null,
    val password: String? = null,
    val method: String? = null,
    val network: String? = null,
    val security: String? = null,
    val flow: String? = null,
    val headerType: String? = null,
    val hostHeader: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
    val mode: String? = null,
    val authority: String? = null,
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val allowInsecure: Boolean = false,
    val pingMs: Long? = null,
) {
    val isAuto: Boolean
        get() = protocol == ProxyProtocol.AUTO

    companion object {
        const val AUTO_ID = "auto"

        fun auto() = VpnServer(
            id = AUTO_ID,
            protocol = ProxyProtocol.AUTO,
            displayName = "Авто",
            host = "",
            port = 0,
            rawLink = "",
        )
    }
}
