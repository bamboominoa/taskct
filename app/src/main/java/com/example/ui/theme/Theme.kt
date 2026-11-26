package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = TelegramBlue,
    onSecondary = Color.White,
    secondaryContainer = TelegramContainerLight,
    onSecondaryContainer = TelegramOnContainerLight,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = CoralOnContainerLight,
    background = WarmIvoryBackground,
    onBackground = SlateMidnight,
    surface = CrispSurfaceLight,
    onSurface = SlateMidnight,
    surfaceVariant = SoftSurfaceVariantLight,
    onSurfaceVariant = SlateMuted,
    outline = SlateBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = TealBright,
    onPrimary = SlateMidnight,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = TelegramBlueLight,
    onSecondary = SlateMidnight,
    secondaryContainer = TelegramContainerDark,
    onSecondaryContainer = TelegramOnContainerDark,
    tertiary = AmberBright,
    onTertiary = SlateMidnight,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    error = CoralErrorDark,
    onError = SlateMidnight,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralOnContainerDark,
    background = DeepSlateBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DeepSlateSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DeepSlateSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = SlateBorderDark,
    outlineVariant = Color(0xFF1E293B)
)

val TaskPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TaskPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TaskPulseShapes,
        content = content
    )
}
