package com.willg.superear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SuperEarColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = OnPrimary,
    secondary = PrimaryTealVariant,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    error = ErrorRed,
    onError = Color.Black
)

@Composable
fun SuperEarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SuperEarColorScheme,
        typography = AppTypography,
        content = content
    )
}
