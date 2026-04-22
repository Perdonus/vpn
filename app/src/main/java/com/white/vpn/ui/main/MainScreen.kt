package com.white.vpn.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.white.vpn.R
import com.white.vpn.data.SubscriptionMode
import com.white.vpn.domain.SplitTunnelMode
import com.white.vpn.vpn.TunnelStatus
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MainScreen(
    state: MainUiState,
    onToggleConnection: () -> Unit,
    onOpenChannel: () -> Unit,
    onSelectSubscriptionMode: (SubscriptionMode) -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onSelectSplitTunnelMode: (SplitTunnelMode) -> Unit,
    onToggleSplitTunnelPackage: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    val status = state.connection.status
    val isBusy = status == TunnelStatus.CONNECTING || status == TunnelStatus.STOPPING
    val uptime = rememberUptimeLabel(state.connection.startedAtEpochMs, status)
    val infiniteTransition = rememberInfiniteTransition(label = "whitevpn-button-transition")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "whitevpn-halo-scale",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
        label = "whitevpn-wave-phase",
    )
    val buttonScale by animateFloatAsState(
        targetValue =
            when (status) {
                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING -> 0.985f

                TunnelStatus.CONNECTED -> 1.01f
                else -> 1f
            },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "whitevpn-button-scale",
    )
    val buttonTextColor = if (darkTheme) Color.White else Color.Black
    val buttonFillColor by animateColorAsState(
        targetValue =
            when (status) {
                TunnelStatus.ERROR -> if (darkTheme) Color(0xFF351919) else Color(0xFFF6DEDE)
                TunnelStatus.PERMISSION_REQUIRED -> if (darkTheme) Color(0xFF1A2123) else Color(0xFFF2F4F4)
                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING -> if (darkTheme) Color(0xFF111B1D) else Color(0xFFF5F7F7)

                TunnelStatus.CONNECTED -> if (darkTheme) Color(0xFF123834) else Color(0xFFD7F0E5)
                TunnelStatus.IDLE -> Color.Transparent
            },
        animationSpec = tween(280),
        label = "whitevpn-fill-color",
    )
    val buttonBorderColor by animateColorAsState(
        targetValue =
            when (status) {
                TunnelStatus.ERROR -> Color(0xFFC64646)
                TunnelStatus.PERMISSION_REQUIRED ->
                    colorScheme.outline.copy(alpha = if (darkTheme) 0.58f else 0.42f)

                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING -> colorScheme.primary.copy(alpha = if (darkTheme) 0.92f else 0.78f)

                TunnelStatus.CONNECTED -> colorScheme.primary
                TunnelStatus.IDLE -> colorScheme.outline.copy(alpha = if (darkTheme) 0.58f else 0.36f)
            },
        animationSpec = tween(280),
        label = "whitevpn-border-color",
    )
    val haloColor by animateColorAsState(
        targetValue =
            when (status) {
                TunnelStatus.CONNECTING,
                TunnelStatus.STOPPING -> colorScheme.primary.copy(alpha = if (darkTheme) 0.26f else 0.18f)

                TunnelStatus.CONNECTED -> colorScheme.primary.copy(alpha = if (darkTheme) 0.34f else 0.24f)
                else -> Color.Transparent
            },
        animationSpec = tween(280),
        label = "whitevpn-halo-color",
    )
    val detailLabel =
        when (status) {
            TunnelStatus.CONNECTED ->
                state.connection.activePingMs?.let { stringResource(R.string.status_ping_value, it) }
                    ?: stringResource(R.string.status_ping_unknown)

            TunnelStatus.CONNECTING -> state.connection.message ?: stringResource(R.string.status_connecting)
            TunnelStatus.STOPPING -> state.connection.message ?: stringResource(R.string.status_stopping)
            TunnelStatus.PERMISSION_REQUIRED -> state.connection.message ?: stringResource(R.string.status_permission_required)
            TunnelStatus.ERROR -> state.message ?: stringResource(R.string.status_error_generic)
            TunnelStatus.IDLE -> ""
        }
    var isSplitTunnelSheetOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        AnimatedVisibility(
            visible = state.settings.showChannelPrompt,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 20.dp),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            ChannelPrompt(onOpenChannel = onOpenChannel)
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            VpnCircleButton(
                status = status,
                uptime = uptime,
                detailLabel = detailLabel,
                textColor = buttonTextColor,
                fillColor = buttonFillColor,
                borderColor = buttonBorderColor,
                haloColor = haloColor,
                haloScale = haloScale,
                wavePhase = wavePhase,
                scale = buttonScale,
                onToggleConnection = onToggleConnection,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.mode_title),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurface.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                textAlign = TextAlign.Center,
            )

            SubscriptionModeToggle(
                selectedMode = state.settings.subscriptionMode,
                enabled = !isBusy && !state.isRefreshing,
                onSelect = onSelectSubscriptionMode,
            )

            SplitTunnelButton(
                enabled = !isBusy,
                onClick = {
                    onOpenSplitTunnel()
                    isSplitTunnelSheetOpen = true
                },
            )
        }

        if (isSplitTunnelSheetOpen) {
            SplitTunnelSheet(
                state = state,
                onDismiss = { isSplitTunnelSheetOpen = false },
                onSelectMode = onSelectSplitTunnelMode,
                onTogglePackage = onToggleSplitTunnelPackage,
            )
        }
    }
}

@Composable
private fun VpnCircleButton(
    status: TunnelStatus,
    uptime: String,
    detailLabel: String,
    textColor: Color,
    fillColor: Color,
    borderColor: Color,
    haloColor: Color,
    haloScale: Float,
    wavePhase: Float,
    scale: Float,
    onToggleConnection: () -> Unit,
) {
    val isRunning = status == TunnelStatus.CONNECTED
    val isBusy = status == TunnelStatus.CONNECTING || status == TunnelStatus.STOPPING
    val isPermissionRequired = status == TunnelStatus.PERMISSION_REQUIRED
    val hasError = status == TunnelStatus.ERROR

    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = isRunning || isBusy,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(224.dp)
                        .scale(if (isBusy) 1f + (wavePhase * 0.06f) else haloScale)
                        .border(width = 1.dp, color = haloColor, shape = CircleShape),
            )
        }

        Box(
            modifier =
                Modifier
                    .size(188.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(
                        width = if (isRunning || isBusy) 2.dp else 1.5.dp,
                        color = borderColor,
                        shape = CircleShape,
                    )
                    .clickable(enabled = !isBusy, onClick = onToggleConnection),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isRunning ->
                    ConnectedButtonContent(
                        uptime = uptime,
                        detail = detailLabel,
                        textColor = textColor,
                    )

                isBusy ->
                    BusyButtonContent(
                        label = detailLabel,
                        textColor = textColor,
                        waveColor = borderColor,
                        phase = wavePhase,
                    )

                isPermissionRequired || hasError ->
                    StatusButtonContent(
                        label = detailLabel,
                        textColor = textColor,
                    )

                else ->
                    IdleButtonContent(
                        textColor = textColor,
                    )
            }
        }
    }
}

@Composable
private fun ChannelPrompt(onOpenChannel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.offset(y = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        ) {
            Text(
                text = stringResource(R.string.channel_prompt),
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .clickable(onClick = onOpenChannel)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenChannel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_telegram),
                contentDescription = stringResource(R.string.channel_subscribe),
                modifier = Modifier.size(30.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun SubscriptionModeToggle(
    selectedMode: SubscriptionMode,
    enabled: Boolean,
    onSelect: (SubscriptionMode) -> Unit,
) {
    val modes = listOf(SubscriptionMode.AUTO, SubscriptionMode.MOBILE, SubscriptionMode.MOBILE_2)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEach { mode ->
                val selected = mode == selectedMode
                val backgroundColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(220),
                    label = "subscription-mode-background",
                )
                val contentColor by animateColorAsState(
                    targetValue =
                        when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                        },
                    animationSpec = tween(220),
                    label = "subscription-mode-content",
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(backgroundColor)
                            .clickable(enabled = enabled && !selected) {
                                onSelect(mode)
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            when (mode) {
                                SubscriptionMode.AUTO -> stringResource(R.string.server_auto)
                                SubscriptionMode.MOBILE -> stringResource(R.string.mode_one)
                                SubscriptionMode.MOBILE_2 -> stringResource(R.string.mode_two)
                            },
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.split_tunnel_button),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    },
            )
        }
    }
}

@Composable
private fun ConnectedButtonContent(
    uptime: String,
    detail: String,
    textColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 18.dp),
    ) {
        Text(
            text = uptime.ifBlank { "00:00:00" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.titleMedium,
            color = textColor.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BusyButtonContent(
    label: String,
    textColor: Color,
    waveColor: Color,
    phase: Float,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        WaveLoadingLayer(
            modifier = Modifier.matchParentSize(),
            color = waveColor,
            phase = phase,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = textColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusButtonContent(
    label: String,
    textColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = textColor,
        )
        AnimatedVisibility(
            visible = label.isNotBlank(),
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IdleButtonContent(
    textColor: Color,
) {
    Icon(
        imageVector = Icons.Rounded.PowerSettingsNew,
        contentDescription = "Toggle connection",
        modifier = Modifier.size(78.dp),
        tint = textColor,
    )
}

@Composable
private fun WaveLoadingLayer(
    modifier: Modifier,
    color: Color,
    phase: Float,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.07f
        repeat(3) { index ->
            val amplitude = size.height * (0.018f + (index * 0.005f))
            val baseY = size.height * (0.37f + (index * 0.12f))
            val wavelength = size.width * 0.72f
            val path = Path()
            var x = -8f
            var firstPoint = true

            while (x <= size.width + 8f) {
                val normalized = (x / wavelength) + phase + (index * 0.18f)
                val y = baseY + sin(normalized * (2f * PI.toFloat())) * amplitude
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
                x += 6f
            }

            drawPath(
                path = path,
                color = color.copy(alpha = 0.16f + (index * 0.12f)),
                style =
                    Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }
}

@Composable
private fun rememberUptimeLabel(
    startedAtEpochMs: Long?,
    status: TunnelStatus,
): String =
    produceState(
        initialValue = "",
        key1 = startedAtEpochMs,
        key2 = status,
    ) {
        if (startedAtEpochMs == null || status != TunnelStatus.CONNECTED) {
            value = ""
            return@produceState
        }
        while (true) {
            value = formatUptime(startedAtEpochMs)
            delay(1_000L)
        }
    }.value

private fun formatUptime(startedAtEpochMs: Long): String {
    val elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1_000L).coerceAtLeast(0L)
    val hours = elapsedSeconds / 3_600L
    val minutes = (elapsedSeconds % 3_600L) / 60L
    val seconds = elapsedSeconds % 60L
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
