package com.white.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme =
    lightColorScheme(
        primary = Teal,
        onPrimary = Cloud,
        secondary = Mint,
        onSecondary = Cloud,
        tertiary = TealDark,
        background = Cream,
        onBackground = Ink,
        surface = Cloud,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = InkSoft,
        outline = InkSoft,
    )

@Composable
fun WhiteVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
