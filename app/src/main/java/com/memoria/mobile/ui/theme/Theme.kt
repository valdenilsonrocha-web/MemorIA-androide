package com.memoria.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun lightColorsFor(preset: ThemePreset) = lightColorScheme(
    // `--primary-color` exactly as `applyThemePreset()` sets it: buttons, active
    // tab, page titles and the header all use the palette's raw primary on the
    // web, white text on top (`.btn-primary { color: white }`).
    primary = preset.primary,
    onPrimary = Color.White,
    primaryContainer = preset.light,
    onPrimaryContainer = preset.dark,
    secondary = preset.secondary,
    onSecondary = Color.White,
    // Material's defaults for these are lavender; the web has none of that —
    // chips, selected pills and tonal buttons read in the palette's own family.
    secondaryContainer = preset.light,
    onSecondaryContainer = preset.dark,
    tertiary = preset.secondary,
    tertiaryContainer = preset.light,
    onTertiaryContainer = preset.dark,
    // `--bg-tertiary` for tracks and filled fields; white cards/dialogs/menus
    // (`--bg-primary`), no tonal tint over elevated surfaces.
    surfaceVariant = SurfaceTertiary,
    surfaceTint = Color.White,
    surfaceBright = Color.White,
    surfaceDim = SurfaceTertiary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = SurfaceTertiary,
    inverseSurface = TextPrimary,
    inverseOnSurface = Color.White,
    inversePrimary = preset.light,
    scrim = Color.Black,
    // A web tinge a página inteira com a paleta escolhida (`bgSecondary` e
    // `border` de `getThemePalettes()`), não só os acentos — é o que faz um tema
    // parecer aplicado em vez de decorativo. Os tons são quase brancos, por isso
    // o texto escuro por cima mantém o contraste.
    background = preset.background,
    onBackground = TextPrimary,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = preset.border,
    outlineVariant = preset.border,
    error = RedMiss,
)

private fun darkColorsFor(preset: ThemePreset) = darkColorScheme(
    primary = preset.primary,
    onPrimary = preset.dark,
    primaryContainer = preset.dark,
    onPrimaryContainer = preset.light,
    secondary = preset.secondary,
    background = SurfaceDark,
    error = RedMiss,
)

/**
 * Type scale from `main.css` (`--font-size-base` 16–18px, `--font-size-large`
 * 19–22px, `--font-size-xlarge` 24–28px). The web never goes below 13px — the
 * bottom-nav label — because the people using it are mostly elderly, so the
 * Material defaults (14sp body, 12sp labels) are raised to match.
 */
private val AppTypography = Typography().let { base ->
    Typography(
        headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp),
        // `.btn-primary`: font-size var(--font-size-large), weight 600.
        labelLarge = base.labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        // `.nav-item`: 13px, weight 500.
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp),
    )
}

/**
 * `--border-radius` 16px (inputs, setting cards) and `--border-radius-lg` 24px
 * (stat/medication cards, buttons, the "Mais" sheet).
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    // `.modal-content { border-radius: var(--border-radius) }` — dialogs are 16px.
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * The palette in use, raw (the same values as [MaterialTheme.colorScheme]).
 * Kept for the places that paint a FILL the way the web does — the
 * blue header (`.header { background: var(--primary-color) }`) — need the
 * palette's own `primary`.
 */
val LocalThemePreset = staticCompositionLocalOf { ThemePreset.PASTEL_AZUL }

/**
 * [themePreset] is the palette id stored by Settings; an unknown one falls back
 * to the web app's default rather than throwing, so a preference written by a
 * newer build never bricks an older one.
 */
@Composable
fun MemoriaTheme(
    // The web has no dark mode; kept as a parameter only for previews.
    darkTheme: Boolean = false,
    themePreset: String? = null,
    content: @Composable () -> Unit,
) {
    val preset = ThemePreset.from(themePreset)
    CompositionLocalProvider(LocalThemePreset provides preset) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorsFor(preset) else lightColorsFor(preset),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
