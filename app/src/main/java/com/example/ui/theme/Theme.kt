package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color.Black,
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisCyanGlow,
    secondary = JarvisElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurfaceHighlight,
    onSecondaryContainer = JarvisCyanGlow,
    tertiary = JarvisGold,
    onTertiary = Color.Black,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorder,
    outlineVariant = JarvisBorderBright,
    error = JarvisRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
