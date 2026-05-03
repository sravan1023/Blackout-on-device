package com.example.blackout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackoutColorScheme = lightColorScheme(
    primary = TealAccent,
    onPrimary = CardWhite,
    primaryContainer = TealWash,
    onPrimaryContainer = NavyPrimary,
    secondary = NavyPrimary,
    onSecondary = CardWhite,
    background = BackgroundWarm,
    onBackground = NavyPrimary,
    surface = CardWhite,
    onSurface = NavyPrimary,
    surfaceVariant = BackgroundWarm,
    onSurfaceVariant = Color(0xFF666666),
    outline = Hairline,
)

@Composable
fun BlackoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlackoutColorScheme,
        typography = BlackoutTypography,
        content = content
    )
}
