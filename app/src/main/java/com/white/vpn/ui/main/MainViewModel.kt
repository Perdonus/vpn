package com.white.vpn.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.white.vpn.data.RefreshResult
import com.white.vpn.data.ServerRepository
import com.white.vpn.domain.AppSettings
import com.white.vpn.domain.VpnServer
import com.white.vpn.vpn.VpnManager
import com.white.vpn.vpn.VpnRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val serverRepository: ServerRepository,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> =
        combine(
            serverRepository.settings,
            VpnManager.state,
            isRefreshing,
            message,
        ) { settings, connection, refreshing, banner ->
            MainUiState(
                settings = settings,
                connection = connection,
                isRefreshing = refreshing,
                message = banner ?: connection.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    init {
        viewModelScope.launch {
            if (uiState.value.settings.servers.isEmpty()) {
                refreshSubscription()
            }
        }
    }

    fun refreshSubscription() {
        viewModelScope.launch {
            isRefreshing.value = true
            runCatching { serverRepository.refreshSubscription() }
                .onSuccess(::handleRefreshSuccess)
                .onFailure { error ->
                    message.value = error.message ?: "Не удалось обновить подписку"
                }
            isRefreshing.value = false
        }
    }

    fun saveSubscriptionUrl(url: String) {
        viewModelScope.launch {
            isRefreshing.value = true
            runCatching {
                serverRepository.refreshSubscription(url)
            }.onSuccess {
                handleRefreshSuccess(it)
            }.onFailure { error ->
                message.value = error.message ?: "Не удалось сохранить URL"
            }
            isRefreshing.value = false
        }
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            serverRepository.selectServer(serverId)
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun handleRefreshSuccess(result: RefreshResult) {
        message.value =
            if (result.importedServers.isEmpty()) {
                "Подписка обновлена, но серверы не найдены"
            } else {
                "Загружено серверов: ${result.importedServers.size}"
            }
    }
}

data class MainUiState(
    val settings: AppSettings = AppSettings(subscriptionUrl = com.white.vpn.data.AppDefaults.DEFAULT_SUBSCRIPTION_URL),
    val connection: VpnRuntimeState = VpnRuntimeState(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
) {
    val servers: List<VpnServer>
        get() = settings.serversWithAuto

    val selectedServer: VpnServer?
        get() = servers.firstOrNull { it.id == settings.selectedServerId } ?: servers.firstOrNull()

    val selectedServerLabel: String
        get() = selectedServer?.displayName ?: "Авто"

    val manualRequestedProfileId: String?
        get() = settings.selectedServerId.takeUnless { it == VpnServer.AUTO_ID }
}

