package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette in the style of KSPlanner: dark slate panels with thin borders and an amber accent.
private val Dark = darkColorScheme(
    primary = Color(0xFFF5C542),
    onPrimary = Color(0xFF2B2100),
    primaryContainer = Color(0xFF4A3B0A),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFE0B341),
    onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFF3A3420),
    onSecondaryContainer = Color(0xFFF2E3B3),
    background = Color(0xFF16171B),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF16171B),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF24262C),
    onSurfaceVariant = Color(0xFFB4B1A9),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF25272D),
    surfaceContainerHighest = Color(0xFF2C2F36),
    surfaceContainerLow = Color(0xFF1B1C21),
    surfaceContainerLowest = Color(0xFF121317),
    outline = Color(0xFF4A4D55),
    outlineVariant = Color(0xFF33363D),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A6500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF2B2100),
    secondary = Color(0xFF7A6420),
    secondaryContainer = Color(0xFFF2E3B3),
    onSecondaryContainer = Color(0xFF2B2100),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFF7F5EF),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFECE7DA),
    onSurfaceVariant = Color(0xFF5C574B),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EDE3),
    surfaceContainerHighest = Color(0xFFE9E4D8),
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFFC9C2B0),
    outlineVariant = Color(0xFFDDD7C8),
    error = Color(0xFFB3261E),
)

@Composable
fun KsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
