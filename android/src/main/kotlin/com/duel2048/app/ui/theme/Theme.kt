package com.duel2048.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalPalette = staticCompositionLocalOf { Palettes.NEON }

/** True when the user asked for reduced effects (no ambient animation, particles or glow). */
val LocalLowEffects = staticCompositionLocalOf { false }

private val DuelTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun DuelTheme(palette: DuelPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.bgBottom,
        secondary = palette.accent2,
        onSecondary = palette.bgBottom,
        background = palette.bgBottom,
        onBackground = palette.textPrimary,
        surface = palette.bgTop,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.bgTop,
        onSurfaceVariant = palette.textSecondary,
        error = palette.danger,
        outline = palette.surfaceBorder,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = DuelTypography, content = content)
    }
}
