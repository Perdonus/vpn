package com.white.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightAppColorScheme =
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

private val DarkAppColorScheme =
    darkColorScheme(
        primary = NightPrimary,
        onPrimary = Night,
        secondary = NightSecondary,
        onSecondary = NightOnBackground,
        tertiary = Mint,
        background = Night,
        onBackground = NightOnBackground,
        surface = NightSurface,
        onSurface = NightOnSurface,
        surfaceVariant = NightSurfaceSoft,
        onSurfaceVariant = NightOnSurfaceSoft,
        outline = NightOutline,
    )

@Composable
fun WhiteVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkAppColorScheme else LightAppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
