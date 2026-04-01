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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.white.vpn.R
import com.white.vpn.vpn.TunnelStatus
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    state: MainUiState,
    onToggleConnection: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val status = state.connection.status
    val isRunning = status == TunnelStatus.CONNECTED
    val isBusy = status == TunnelStatus.CONNECTING || status == TunnelStatus.STOPPING
    val isPermissionRequired = status == TunnelStatus.PERMISSION_REQUIRED
    val hasError = status == TunnelStatus.ERROR
    val uptime = rememberUptimeLabel(state.connection.startedAtEpochMs, state.connection.status)
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
        targetValue = if (isRunning) colorScheme.onPrimary else colorScheme.onSurface,
        animationSpec = tween(500),
        label = "whitevpn-button-tint",
    )
    val coreBrush =
        when {
            hasError ->
                Brush.radialGradient(
                    colors = listOf(colorScheme.surface, Color(0xFFFFD7D7), Color(0xFF8E1D1D)),
                )

            isBusy || isPermissionRequired ->
                Brush.radialGradient(
                    colors = listOf(colorScheme.surface, colorScheme.secondary, colorScheme.tertiary),
                )

            isRunning ->
                Brush.radialGradient(
                    colors = listOf(colorScheme.surface, colorScheme.secondary, colorScheme.primary),
                )

            else ->
                Brush.radialGradient(
                    colors = listOf(colorScheme.surface, colorScheme.surfaceVariant, colorScheme.background),
                )
        }
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.background,
                                colorScheme.surface,
                                colorScheme.surfaceVariant,
                            ),
                        ),
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier
                            .size(236.dp)
                            .scale(if (isRunning) pulseScale else 1f)
                            .alpha(if (isRunning) 0.22f else 0f)
                            .background(
                                brush = Brush.radialGradient(colors = listOf(colorScheme.primary.copy(alpha = 0.55f), Color.Transparent)),
                                shape = CircleShape,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(188.dp)
                            .scale(buttonScale)
                            .shadow(
                                30.dp,
                                CircleShape,
                                ambientColor = colorScheme.primary.copy(alpha = 0.28f),
                                spotColor = colorScheme.surfaceVariant.copy(alpha = 0.24f),
                            )
                            .clip(CircleShape)
                            .background(coreBrush)
                            .border(
                                width = 1.5.dp,
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (isRunning || isBusy) 0.75f else 0.35f),
                                            Color.Transparent,
                                        ),
                                    ),
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
                            )

                        isBusy || isPermissionRequired || hasError ->
                            StatusButtonContent(
                                label = detailLabel,
                                rotation = busyRotation,
                                tint = buttonTint,
                            )

                        else ->
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Toggle connection",
                                modifier =
                                    Modifier
                                        .size(78.dp)
                                        .graphicsLayer {
                                            rotationZ = if (isBusy) busyRotation else 0f
                                        },
                                tint = buttonTint,
                            )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onOpenChannel,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    .fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.84f),
                    contentColor = colorScheme.onSurface,
                ),
        ) {
            Text(text = stringResource(R.string.channel_subscribe))
        }
    }
}

@Composable
private fun ConnectedButtonContent(
    uptime: String,
    detail: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 18.dp),
    ) {
        Text(
            text = uptime.ifBlank { "00:00:00" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusButtonContent(
    label: String,
    rotation: Float,
    tint: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            modifier =
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                    },
            tint = tint,
        )
        AnimatedVisibility(
            visible = label.isNotBlank(),
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
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
