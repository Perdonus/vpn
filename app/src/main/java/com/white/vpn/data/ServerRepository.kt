package com.white.vpn.data

import com.white.vpn.domain.AppSettings
import com.white.vpn.domain.SplitTunnelMode
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
        val subscriptionUrl = urlOverride?.trim().takeUnless { it.isNullOrEmpty() }
        val subscriptionUrls =
            when {
                subscriptionUrl != null -> listOf(subscriptionUrl)
                current.subscriptionUrl.trim().ifEmpty { AppDefaults.DEFAULT_SUBSCRIPTION_URL } == AppDefaults.DEFAULT_SUBSCRIPTION_URL ->
                    AppDefaults.DEFAULT_SUBSCRIPTION_URLS

                else -> listOf(current.subscriptionUrl)
            }
        if (subscriptionUrl != null) {
            settingsStore.setSubscriptionUrl(subscriptionUrl)
        }

        val servers = loadServers(subscriptionUrls)
        settingsStore.replaceServers(servers)
        settingsStore.resetSelectedServerIfMissing()
        RefreshResult(
            url = subscriptionUrl ?: current.subscriptionUrl,
            importedServers = servers,
        )
    }

    suspend fun updateSubscriptionUrl(url: String) {
        settingsStore.setSubscriptionUrl(url)
    }

    suspend fun selectServer(serverId: String) {
        settingsStore.setSelectedServerId(serverId)
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        settingsStore.setSplitTunnelMode(mode)
    }

    suspend fun toggleSplitTunnelPackage(packageName: String) {
        settingsStore.toggleSplitTunnelPackage(packageName)
    }

    suspend fun dismissChannelPrompt() {
        settingsStore.setShowChannelPrompt(false)
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

    private fun loadServers(urls: List<String>): List<VpnServer> {
        val imported = mutableListOf<VpnServer>()
        var failure: Throwable? = null

        urls.forEach { url ->
            runCatching {
                SubscriptionImporter.import(download(url))
            }.onSuccess { servers ->
                imported += servers
            }.onFailure { error ->
                failure = failure ?: error
            }
        }

        if (imported.isEmpty()) {
            throw (failure ?: IllegalStateException("Subscription request returned no servers"))
        }

        return imported.deduplicateServers()
    }

    private fun List<VpnServer>.deduplicateServers(): List<VpnServer> {
        if (isEmpty()) return this
        val unique = linkedMapOf<String, VpnServer>()
        for (server in this) {
            unique.putIfAbsent(server.dedupeKey(), server)
        }
        return unique.values.toList()
    }
}

data class RefreshResult(
    val url: String,
    val importedServers: List<VpnServer>,
)
