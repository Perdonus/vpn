package com.perdonus.vpn.domain

enum class ProxyProtocol(val scheme: String) {
    AUTO("auto"),
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("ss"),
    SOCKS("socks");

    companion object {
        fun fromScheme(raw: String?): ProxyProtocol? {
            val scheme = raw?.lowercase() ?: return null
            return entries.firstOrNull { it.scheme == scheme }
        }
    }
}
