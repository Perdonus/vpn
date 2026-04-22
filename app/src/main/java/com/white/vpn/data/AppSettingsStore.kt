package com.white.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.white.vpn.domain.AppSettings
import com.white.vpn.domain.SplitTunnelMode
import com.white.vpn.domain.VpnServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private const val DATASTORE_NAME = "vpn_preferences"

private val Context.vpnDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class AppSettingsStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val serversSerializer = ListSerializer(VpnServer.serializer())

    val settings: Flow<AppSettings> =
        context.vpnDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                AppSettings(
                    subscriptionUrl = storedSubscriptionUrl(preferences[Keys.SubscriptionUrl]),
                    selectedServerId = preferences[Keys.SelectedServerId] ?: VpnServer.AUTO_ID,
                    splitTunnelMode = storedSplitTunnelMode(preferences[Keys.SplitTunnelModeId]),
                    splitTunnelPackages = preferences[Keys.SplitTunnelPackages].orEmpty().filter { it.isNotBlank() }.toSet(),
                    showChannelPrompt = preferences[Keys.ShowChannelPrompt] ?: true,
                    servers = decodeServers(preferences[Keys.ServersJson]),
                    lastSubscriptionSyncEpochMs = preferences[Keys.LastSyncEpochMs],
                )
            }

    suspend fun setSubscriptionUrl(url: String) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.SubscriptionUrl] = storedSubscriptionUrl(url)
        }
    }

    suspend fun setSelectedServerId(serverId: String) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.SelectedServerId] = serverId.ifBlank { VpnServer.AUTO_ID }
        }
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.SplitTunnelModeId] = mode.name
        }
    }

    suspend fun toggleSplitTunnelPackage(packageName: String) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isEmpty()) return
        context.vpnDataStore.edit { preferences ->
            val updated =
                preferences[Keys.SplitTunnelPackages]
                    .orEmpty()
                    .toMutableSet()
                    .apply {
                        if (!add(normalizedPackageName)) {
                            remove(normalizedPackageName)
                        }
                    }
                    .filter { it.isNotBlank() }
                    .toSet()
            preferences[Keys.SplitTunnelPackages] = updated
        }
    }

    suspend fun setShowChannelPrompt(show: Boolean) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.ShowChannelPrompt] = show
        }
    }

    suspend fun replaceServers(servers: List<VpnServer>) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.ServersJson] = json.encodeToString(serversSerializer, servers.map { it.sanitizeForStorage() })
            preferences[Keys.LastSyncEpochMs] = System.currentTimeMillis()
        }
    }

    suspend fun updatePing(serverId: String, pingMs: Long?) {
        context.vpnDataStore.edit { preferences ->
            val updated = decodeServers(preferences[Keys.ServersJson]).map { server ->
                if (server.id == serverId) {
                    server.copy(pingMs = pingMs)
                } else {
                    server
                }
            }
            preferences[Keys.ServersJson] = json.encodeToString(serversSerializer, updated.map { it.sanitizeForStorage() })
        }
    }

    suspend fun updatePings(pingsByServerId: Map<String, Long?>) {
        if (pingsByServerId.isEmpty()) return
        context.vpnDataStore.edit { preferences ->
            val updated = decodeServers(preferences[Keys.ServersJson]).map { server ->
                if (pingsByServerId.containsKey(server.id)) {
                    server.copy(pingMs = pingsByServerId[server.id])
                } else {
                    server
                }
            }
            preferences[Keys.ServersJson] = json.encodeToString(serversSerializer, updated.map { it.sanitizeForStorage() })
        }
    }

    suspend fun resetSelectedServerIfMissing() {
        context.vpnDataStore.edit { preferences ->
            val selected = preferences[Keys.SelectedServerId] ?: VpnServer.AUTO_ID
            if (selected == VpnServer.AUTO_ID) return@edit
            val exists = decodeServers(preferences[Keys.ServersJson]).any { it.id == selected }
            if (!exists) {
                preferences[Keys.SelectedServerId] = VpnServer.AUTO_ID
            }
        }
    }

    private fun decodeServers(raw: String?): List<VpnServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serversSerializer, raw) }
            .getOrDefault(emptyList())
            .filterNot(VpnServer::hasIpv6Label)
    }

    private fun VpnServer.sanitizeForStorage(): VpnServer = copy(rawLink = "")

    private fun storedSubscriptionUrl(storedUrl: String?): String {
        val normalizedUrl = storedUrl?.trim().orEmpty().ifEmpty { AppDefaults.DEFAULT_SUBSCRIPTION_URL }
        return if (AppDefaults.isBundledSubscriptionUrl(normalizedUrl)) {
            AppDefaults.DEFAULT_SUBSCRIPTION_URL
        } else {
            normalizedUrl
        }
    }

    private fun storedSplitTunnelMode(storedModeId: String?): SplitTunnelMode =
        storedModeId
            ?.let { modeId -> SplitTunnelMode.entries.firstOrNull { it.name == modeId } }
            ?: SplitTunnelMode.OFF

    private object Keys {
        val SubscriptionUrl = stringPreferencesKey("subscription_url")
        val SelectedServerId = stringPreferencesKey("selected_server_id")
        val SplitTunnelModeId = stringPreferencesKey("split_tunnel_mode_id")
        val SplitTunnelPackages = stringSetPreferencesKey("split_tunnel_packages")
        val ShowChannelPrompt = booleanPreferencesKey("show_channel_prompt")
        val ServersJson = stringPreferencesKey("servers_json")
        val LastSyncEpochMs = longPreferencesKey("last_sync_epoch_ms")
    }
}
