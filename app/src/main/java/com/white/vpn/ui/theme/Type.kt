package com.white.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.white.vpn.R

private val UnboundedFontFamily =
    FontFamily(
        Font(R.font.unbounded_regular, FontWeight.Normal),
        Font(R.font.unbounded_medium, FontWeight.Medium),
        Font(R.font.unbounded_semibold, FontWeight.SemiBold),
        Font(R.font.unbounded_bold, FontWeight.Bold),
    )

val AppTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = UnboundedFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = UnboundedFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = UnboundedFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = UnboundedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = UnboundedFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
    )
