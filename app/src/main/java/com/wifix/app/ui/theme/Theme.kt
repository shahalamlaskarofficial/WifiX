package com.wifix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WiFiXColors {
    val CanvasCharcoal = Color(0xFF0A0D14)
    val SurfaceDeep = Color(0xFF111622)
    val GlassFill = Color(0x14FFFFFF)
    val GlassFillStrong = Color(0x1FFFFFFF)
    val GlassBorder = Color(0x28FFFFFF)
    val AccentBlue = Color(0xFF3B82F6)
    val AccentBlueSoft = Color(0xFF60A5FA)
    val AccentViolet = Color(0xFF8B5CF6)
    val StatusGood = Color(0xFF10B981)
    val StatusWarn = Color(0xFFF59E0B)
    val StatusError = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF3F6FC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val WiFiXColorScheme = darkColorScheme(
    primary = WiFiXColors.AccentBlue,
    secondary = WiFiXColors.AccentViolet,
    background = WiFiXColors.CanvasCharcoal,
    surface = WiFiXColors.SurfaceDeep,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = WiFiXColors.TextPrimary,
    onSurface = WiFiXColors.TextPrimary,
    error = WiFiXColors.StatusError
)

private val WiFiXTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = WiFiXColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        color = WiFiXColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = WiFiXColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        color = WiFiXColors.TextMuted
    )
)

@Composable
fun WiFiXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WiFiXColorScheme,
        typography = WiFiXTypography,
        content = content
    )
}
