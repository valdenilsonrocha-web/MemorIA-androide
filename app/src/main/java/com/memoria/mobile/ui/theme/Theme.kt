package com.memoria.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = TealDark,
    secondary = Amber,
    background = SurfaceLight,
    surface = androidx.compose.ui.graphics.Color.White,
    error = RedMiss,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = TealLight,
    secondary = Amber,
    background = SurfaceDark,
    error = RedMiss,
)

// Slightly larger type baseline — accessibility for older users.
private val AppTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 17.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MemoriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
