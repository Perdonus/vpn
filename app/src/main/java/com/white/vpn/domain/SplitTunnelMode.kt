package com.white.vpn.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SplitTunnelMode {
    OFF,
    BYPASS,
    INCLUDE,
}

data class SplitTunnelSettings(
    val mode: SplitTunnelMode = SplitTunnelMode.OFF,
    val packages: Set<String> = emptySet(),
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)
