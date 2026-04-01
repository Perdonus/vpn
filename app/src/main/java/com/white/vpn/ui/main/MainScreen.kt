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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.white.vpn.ui.theme.Cloud
import com.white.vpn.ui.theme.Cream
import com.white.vpn.ui.theme.Ink
import com.white.vpn.ui.theme.InkSoft
import com.white.vpn.ui.theme.Mint
import com.white.vpn.ui.theme.Teal
import com.white.vpn.ui.theme.TealDark
import com.white.vpn.vpn.TunnelStatus
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    state: MainUiState,
    onToggleConnection: () -> Unit,
) {
    val isRunning = state.connection.status == TunnelStatus.CONNECTED
    val isBusy = state.connection.status == TunnelStatus.CONNECTING || state.connection.status == TunnelStatus.STOPPING
    val pulseTransition = rememberInfiniteTransition(label = "whitevpn-pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "whitevpn-pulse-scale",
    )
    val busyRotation by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
        label = "whitevpn-busy-rotation",
    )
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isBusy -> 0.94f
            isRunning -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "whitevpn-button-scale",
    )
    val buttonTint by animateColorAsState(
        targetValue = if (isRunning) TealDark else Ink,
        animationSpec = tween(500),
        label = "whitevpn-button-tint",
    )
    val coreBrush =
        if (isRunning) {
            Brush.radialGradient(
                colors = listOf(Color(0xFFF6FFF7), Mint, Teal),
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Cloud, Color(0xFFF6EFE6), Color(0xFFE7D6C4)),
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Cream, Color(0xFFF7EBDC), Color(0xFFEDE0CE)),
                        ),
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AnimatedVisibility(
                visible = isRunning,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                ConnectionMetrics(
                    uptime = rememberUptimeLabel(state.connection.startedAtEpochMs, state.connection.status),
                    ping = state.connection.activePingMs,
                )
            }

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier
                            .size(214.dp)
                            .scale(if (isRunning) pulseScale else 1f)
                            .alpha(if (isRunning) 0.22f else 0f)
                            .background(
                                brush = Brush.radialGradient(colors = listOf(Mint, Color.Transparent)),
                                shape = CircleShape,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(148.dp)
                            .scale(buttonScale)
                            .shadow(30.dp, CircleShape, ambientColor = Color(0x66132020), spotColor = Color(0x33132020))
                            .clip(CircleShape)
                            .background(coreBrush)
                            .clickable(enabled = !isBusy, onClick = onToggleConnection),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Toggle connection",
                        modifier =
                            Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    rotationZ = if (isBusy) busyRotation else 0f
                                },
                        tint = buttonTint,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionMetrics(
    uptime: String,
    ping: Long?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uptime.isNotBlank()) {
            Text(
                text = uptime,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
        }
        ping?.let {
            Text(
                text = "$it ms",
                modifier = Modifier.widthIn(min = 72.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun rememberUptimeLabel(
    startedAtEpochMs: Long?,
    status: TunnelStatus,
): String =
    produceState(initialValue = "", startedAtEpochMs, status) {
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
