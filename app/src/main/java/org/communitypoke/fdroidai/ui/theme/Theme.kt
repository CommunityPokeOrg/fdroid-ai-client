package org.communitypoke.fdroidai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Teal400,
    onPrimary = Navy900,
    secondary = Amber400,
    onSecondary = Navy900,
    background = Navy900,
    onBackground = Cream100,
    surface = Navy800,
    onSurface = Cream100,
    surfaceVariant = Navy700,
    onSurfaceVariant = Grey500,
    error = Red400,
)

private val LightScheme = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    secondary = Amber400,
    onSecondary = Navy900,
    background = Color(0xFFF7FAFC),
    onBackground = Navy900,
    surface = Color.White,
    onSurface = Navy900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF4A5568),
    error = Color(0xFFC53030),
)

@Composable
fun FdroidAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
