package com.perdonus.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.perdonus.vpn.domain.AppSettings
import com.perdonus.vpn.domain.VpnServer
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
                    subscriptionUrl = preferences[Keys.SubscriptionUrl] ?: AppDefaults.DEFAULT_SUBSCRIPTION_URL,
                    selectedServerId = preferences[Keys.SelectedServerId] ?: VpnServer.AUTO_ID,
                    servers = decodeServers(preferences[Keys.ServersJson]),
                    lastSubscriptionSyncEpochMs = preferences[Keys.LastSyncEpochMs],
                )
            }

    suspend fun setSubscriptionUrl(url: String) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.SubscriptionUrl] = url.trim().ifEmpty { AppDefaults.DEFAULT_SUBSCRIPTION_URL }
        }
    }

    suspend fun setSelectedServerId(serverId: String) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.SelectedServerId] = serverId.ifBlank { VpnServer.AUTO_ID }
        }
    }

    suspend fun replaceServers(servers: List<VpnServer>) {
        context.vpnDataStore.edit { preferences ->
            preferences[Keys.ServersJson] = json.encodeToString(serversSerializer, servers)
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
            preferences[Keys.ServersJson] = json.encodeToString(serversSerializer, updated)
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
        return runCatching { json.decodeFromString(serversSerializer, raw) }.getOrDefault(emptyList())
    }

    private object Keys {
        val SubscriptionUrl = stringPreferencesKey("subscription_url")
        val SelectedServerId = stringPreferencesKey("selected_server_id")
        val ServersJson = stringPreferencesKey("servers_json")
        val LastSyncEpochMs = longPreferencesKey("last_sync_epoch_ms")
    }
}
