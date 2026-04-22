package com.white.vpn.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.white.vpn.R
import com.white.vpn.domain.InstalledAppInfo
import com.white.vpn.domain.SplitTunnelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onSelectMode: (SplitTunnelMode) -> Unit,
    onTogglePackage: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedPackages = state.splitTunnelSelectedPackages
    val normalizedQuery = searchQuery.trim()
    val filteredApps =
        remember(state.installedApps, selectedPackages, normalizedQuery) {
            state.installedApps
                .asSequence()
                .filter { app ->
                    normalizedQuery.isBlank() ||
                        app.label.contains(normalizedQuery, ignoreCase = true) ||
                        app.packageName.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedWith(splitTunnelAppComparator(selectedPackages))
                .toList()
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.split_tunnel_button),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            SplitTunnelModeToggle(
                selectedMode = state.settings.splitTunnelMode,
                onSelect = onSelectMode,
            )

            AnimatedVisibility(
                visible = state.settings.splitTunnelMode != SplitTunnelMode.OFF,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text =
                        when (state.settings.splitTunnelMode) {
                            SplitTunnelMode.BYPASS -> stringResource(R.string.split_tunnel_mode_bypass_description)
                            SplitTunnelMode.INCLUDE -> stringResource(R.string.split_tunnel_mode_include_description)
                            SplitTunnelMode.OFF -> ""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                label = {
                    Text(text = stringResource(R.string.split_tunnel_search_label))
                },
                placeholder = {
                    Text(text = stringResource(R.string.split_tunnel_search_placeholder))
                },
            )

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 520.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.split_tunnel_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            SplitTunnelAppRow(
                                app = app,
                                enabled = state.settings.splitTunnelMode != SplitTunnelMode.OFF,
                                checked = app.packageName in selectedPackages,
                                onToggle = { onTogglePackage(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelModeToggle(
    selectedMode: SplitTunnelMode,
    onSelect: (SplitTunnelMode) -> Unit,
) {
    val modes = listOf(SplitTunnelMode.OFF, SplitTunnelMode.BYPASS, SplitTunnelMode.INCLUDE)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            )
                            .clickable(enabled = !selected) { onSelect(mode) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            when (mode) {
                                SplitTunnelMode.OFF -> stringResource(R.string.split_tunnel_mode_off)
                                SplitTunnelMode.BYPASS -> stringResource(R.string.split_tunnel_mode_bypass)
                                SplitTunnelMode.INCLUDE -> stringResource(R.string.split_tunnel_mode_include)
                            },
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelAppRow(
    app: InstalledAppInfo,
    enabled: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(
            packageName = app.packageName,
            label = app.label,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                    },
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
    )
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap(width = 56, height = 56)
                        .asImageBitmap()
                }.getOrNull()
            }
        }

    if (iconBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = label,
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun splitTunnelAppComparator(selectedPackages: Set<String>): Comparator<InstalledAppInfo> =
    Comparator { left, right ->
        val priorityComparison =
            compareValuesBy(
                left,
                right,
                { it.packageName !in selectedPackages },
                { splitTunnelLabelBucket(it.label) },
            )
        if (priorityComparison != 0) {
            return@Comparator priorityComparison
        }

        val labelComparison =
            splitTunnelLabelCollator(left.label).compare(
                left.label.trim(),
                right.label.trim(),
            )
        if (labelComparison != 0) {
            return@Comparator labelComparison
        }

        left.packageName.compareTo(right.packageName, ignoreCase = true)
    }

private fun splitTunnelLabelBucket(label: String): Int {
    val firstLetter = label.trim().firstOrNull { it.isLetter() } ?: return 2
    return when {
        firstLetter in '\u0400'..'\u04FF' || firstLetter in '\u0500'..'\u052F' -> 0
        firstLetter in 'A'..'Z' || firstLetter in 'a'..'z' -> 1
        else -> 2
    }
}

private fun splitTunnelLabelCollator(label: String): Collator =
    when (splitTunnelLabelBucket(label)) {
        0 -> splitTunnelRuCollator
        1 -> splitTunnelEnCollator
        else -> splitTunnelFallbackCollator
    }

private val splitTunnelRuCollator: Collator =
    Collator
        .getInstance(Locale("ru"))
        .apply { strength = Collator.PRIMARY }

private val splitTunnelEnCollator: Collator =
    Collator
        .getInstance(Locale.ENGLISH)
        .apply { strength = Collator.PRIMARY }

private val splitTunnelFallbackCollator: Collator =
    Collator
        .getInstance(Locale.getDefault())
        .apply { strength = Collator.PRIMARY }
