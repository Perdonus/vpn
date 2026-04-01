package com.white.vpn.integration

import com.white.vpn.data.ServerRepository
import com.white.vpn.domain.AppSettings
import com.white.vpn.domain.VpnServer
import com.white.vpn.vpn.TunnelProfile
import com.white.vpn.vpn.TunnelProfileStore
import com.white.vpn.vpn.TunnelSelection
import com.white.vpn.vpn.TunnelSelectionMode
import com.white.vpn.vpn.VpnDependencies
import com.white.vpn.vpn.XrayConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppVpnDependencies(
    private val serverRepository: ServerRepository,
    applicationScope: CoroutineScope,
) : VpnDependencies {
    @Volatile
    private var latestSettings: AppSettings? = null

    override val profileStore: TunnelProfileStore =
        object : TunnelProfileStore {
            override suspend fun getProfiles(): List<TunnelProfile> =
                currentSettings().servers.map { server ->
                    TunnelProfile(
                        id = server.id,
                        displayName = server.displayName,
                        host = server.host,
                        port = server.port,
                        lastPingMs = server.pingMs,
                    )
                }

            override suspend fun getSelection(): TunnelSelection {
                val settings = currentSettings()
                return if (settings.selectedServerId == VpnServer.AUTO_ID) {
                    TunnelSelection(mode = TunnelSelectionMode.AUTO)
                } else {
                    TunnelSelection(
                        mode = TunnelSelectionMode.MANUAL,
                        profileId = settings.selectedServerId,
                    )
                }
            }

            override suspend fun setActiveProfileId(profileId: String?) {
                // Keep the user's selection intact. Runtime state is tracked in VpnManager.
            }

            override suspend fun updatePing(profileId: String, pingMs: Long?) {
                serverRepository.updatePing(profileId, pingMs)
            }

            override suspend fun updatePings(pingsByProfileId: Map<String, Long?>) {
                serverRepository.updatePings(pingsByProfileId)
            }
        }

    override val configFactory: XrayConfigFactory =
        object : XrayConfigFactory {
            override fun buildRuntimeConfig(profile: TunnelProfile): String {
                return XrayConfigBuilder.build(findServer(profile.id), includeTun = true)
            }

            override fun buildPingConfig(profile: TunnelProfile): String {
                return XrayConfigBuilder.build(findServer(profile.id), includeTun = false)
            }
        }

    init {
        serverRepository.settings
            .onEach { latestSettings = it }
            .launchIn(applicationScope)
    }

    private fun currentSettings(): AppSettings {
        return latestSettings ?: AppSettings(subscriptionUrl = com.white.vpn.data.AppDefaults.DEFAULT_SUBSCRIPTION_URL)
    }

    private fun findServer(profileId: String): VpnServer {
        return currentSettings().servers.firstOrNull { it.id == profileId }
            ?: error("VPN server not found: $profileId")
    }
}
