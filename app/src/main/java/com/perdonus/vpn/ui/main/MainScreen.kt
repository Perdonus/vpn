package com.perdonus.vpn.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perdonus.vpn.domain.VpnServer
import com.perdonus.vpn.ui.theme.Cloud
import com.perdonus.vpn.ui.theme.Cream
import com.perdonus.vpn.ui.theme.Ink
import com.perdonus.vpn.ui.theme.InkSoft
import com.perdonus.vpn.ui.theme.Mint
import com.perdonus.vpn.ui.theme.Sand
import com.perdonus.vpn.ui.theme.Teal
import com.perdonus.vpn.ui.theme.TealDark
import com.perdonus.vpn.vpn.TunnelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onToggleConnection: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSaveSubscriptionUrl: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showServerSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editableSubscriptionUrl by remember(state.settings.subscriptionUrl) {
        mutableStateOf(state.settings.subscriptionUrl)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissMessage()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(Cream, Sand, Color(0xFFFFEFE0)),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Perdonus VPN",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                        )
                        Text(
                            text = "Одна кнопка. Один список. Автовыбор по пингу.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSoft,
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Настройки",
                            tint = Teal,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                ConnectionBadge(state = state)

                Spacer(modifier = Modifier.height(28.dp))

                ConnectButton(
                    isRunning = state.connection.isRunning,
                    isBusy = state.connection.status == TunnelStatus.CONNECTING || state.connection.status == TunnelStatus.STOPPING,
                    onClick = onToggleConnection,
                )

                Spacer(modifier = Modifier.height(28.dp))

                StatusCard(state = state)

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onRefreshSubscription,
                        enabled = !state.isRefreshing,
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                            )
                        }
                    }

                    Button(
                        onClick = { showServerSheet = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Сервер",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = state.selectedServerLabel,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            containerColor = Cloud,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Выбор сервера",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn {
                    items(state.servers, key = { it.id }) { server ->
                        ServerRow(
                            server = server,
                            isSelected = server.id == state.settings.selectedServerId,
                            onClick = {
                                onSelectServer(server.id)
                                showServerSheet = false
                            },
                        )
                        HorizontalDivider(color = Sand)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = Cloud,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Подписка",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                )
                OutlinedTextField(
                    value = editableSubscriptionUrl,
                    onValueChange = { editableSubscriptionUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL подписки") },
                    minLines = 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showSettings = false }) {
                        Text("Закрыть")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveSubscriptionUrl(editableSubscriptionUrl)
                            showSettings = false
                        },
                    ) {
                        Text("Сохранить")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: MainUiState) {
    val (label, color) =
        when (state.connection.status) {
            TunnelStatus.CONNECTED -> "Подключено" to Mint
            TunnelStatus.CONNECTING -> "Подключение" to Teal
            TunnelStatus.STOPPING -> "Остановка" to TealDark
            TunnelStatus.ERROR -> "Ошибка" to Color(0xFFB04A2F)
            else -> "Отключено" to InkSoft
        }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
        )
    }
}

@Composable
private fun ConnectButton(
    isRunning: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val gradient =
        Brush.radialGradient(
            colors =
                if (isRunning) {
                    listOf(Mint, Teal, TealDark)
                } else {
                    listOf(Color(0xFFF4DAB2), Teal, TealDark)
                },
        )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(236.dp)
                    .shadow(24.dp, CircleShape)
                    .clip(CircleShape)
                    .background(gradient)
                    .clickable(enabled = !isBusy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Cloud,
                        strokeWidth = 3.dp,
                    )
                }
                Text(
                    text = if (isRunning) "STOP" else "GO",
                    color = Cloud,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = if (isRunning) "Нажми, чтобы выключить" else "Нажми, чтобы подключиться",
                    color = Cloud.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Cloud.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.connection.activeProfileName ?: state.selectedServerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text =
                    buildString {
                        append("Режим: ")
                        append(if (state.settings.selectedServerId == VpnServer.AUTO_ID) "Авто" else "Ручной")
                        state.connection.activePingMs?.let {
                            append("  •  Пинг: ")
                            append(it)
                            append(" ms")
                        }
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = InkSoft,
            )
            Text(
                text = "Серверов в списке: ${state.settings.servers.size}",
                style = MaterialTheme.typography.bodyLarge,
                color = InkSoft,
            )
            if (state.connection.startedAtEpochMs != null) {
                Text(
                    text = "Сессия активна",
                    style = MaterialTheme.typography.labelLarge,
                    color = Teal,
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: VpnServer,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text =
                    if (server.isAuto) {
                        "Приложение само выберет лучший пинг"
                    } else {
                        "${server.protocol.scheme.uppercase()} • ${server.host}:${server.port}"
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = InkSoft,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = server.pingMs?.let { "$it ms" } ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = if (server.pingMs != null) Teal else InkSoft,
            )
            if (isSelected) {
                Text(
                    text = "Выбран",
                    style = MaterialTheme.typography.labelLarge,
                    color = TealDark,
                )
            }
        }
    }
}
