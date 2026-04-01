package com.white.vpn.data

import com.white.vpn.domain.AppSettings
import com.white.vpn.domain.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ServerRepository(
    private val settingsStore: AppSettingsStore,
) {
    val settings: Flow<AppSettings> = settingsStore.settings

    suspend fun refreshSubscription(urlOverride: String? = null): RefreshResult = withContext(Dispatchers.IO) {
        val current = settingsStore.settings.first()
        val subscriptionUrl = urlOverride?.trim().takeUnless { it.isNullOrEmpty() } ?: current.subscriptionUrl
        if (urlOverride != null) {
            settingsStore.setSubscriptionUrl(subscriptionUrl)
        }

        val body = download(subscriptionUrl)
        val servers = SubscriptionImporter.import(body)
        settingsStore.replaceServers(servers)
        settingsStore.resetSelectedServerIfMissing()
        RefreshResult(
            url = subscriptionUrl,
            importedServers = servers,
        )
    }

    suspend fun updateSubscriptionUrl(url: String) {
        settingsStore.setSubscriptionUrl(url)
    }

    suspend fun switchSubscriptionMode(mode: SubscriptionMode): RefreshResult = withContext(Dispatchers.IO) {
        settingsStore.setSubscriptionMode(mode)
        val body = download(mode.subscriptionUrl)
        val servers = SubscriptionImporter.import(body)
        settingsStore.replaceServers(servers)
        settingsStore.resetSelectedServerIfMissing()
        RefreshResult(
            url = mode.subscriptionUrl,
            importedServers = servers,
        )
    }

    suspend fun selectServer(serverId: String) {
        settingsStore.setSelectedServerId(serverId)
    }

    suspend fun updatePing(serverId: String, pingMs: Long?) {
        settingsStore.updatePing(serverId, pingMs)
    }

    suspend fun updatePings(pingsByServerId: Map<String, Long?>) {
        settingsStore.updatePings(pingsByServerId)
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/plain, */*")
            setRequestProperty("User-Agent", "WhiteVPN/1.0")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IllegalStateException("Subscription request failed: ${connection.responseCode}")
            }
            BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        appendLine(line)
                        line = reader.readLine()
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

data class RefreshResult(
    val url: String,
    val importedServers: List<VpnServer>,
)
