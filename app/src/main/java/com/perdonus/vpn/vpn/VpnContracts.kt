package com.perdonus.vpn.vpn

import android.content.Intent

enum class TunnelSelectionMode {
    AUTO,
    MANUAL,
}

data class TunnelSelection(
    val mode: TunnelSelectionMode = TunnelSelectionMode.AUTO,
    val profileId: String? = null,
)

data class TunnelProfile(
    val id: String,
    val displayName: String,
    val lastPingMs: Long? = null,
)

enum class TunnelStatus {
    IDLE,
    PERMISSION_REQUIRED,
    CONNECTING,
    CONNECTED,
    STOPPING,
    ERROR,
}

data class VpnRuntimeState(
    val status: TunnelStatus = TunnelStatus.IDLE,
    val activeProfileId: String? = null,
    val activeProfileName: String? = null,
    val activePingMs: Long? = null,
    val startedAtEpochMs: Long? = null,
    val isAutoMode: Boolean = true,
    val message: String? = null,
    val permissionIntent: Intent? = null,
) {
    val isRunning: Boolean
        get() = status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING
}

interface TunnelProfileStore {
    suspend fun getProfiles(): List<TunnelProfile>
    suspend fun getSelection(): TunnelSelection
    suspend fun setActiveProfileId(profileId: String?)
    suspend fun updatePing(profileId: String, pingMs: Long?)
}

interface XrayConfigFactory {
    fun buildRuntimeConfig(profile: TunnelProfile): String

    fun buildPingConfig(profile: TunnelProfile): String = buildRuntimeConfig(profile)
}

interface VpnDependencies {
    val profileStore: TunnelProfileStore
    val configFactory: XrayConfigFactory

    fun pingTestUrl(): String = "https://cp.cloudflare.com/generate_204"
    fun mtu(): Int = 1500
    fun dnsServers(): List<String> = listOf("1.1.1.1", "1.0.0.1")
}
