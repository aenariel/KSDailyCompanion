package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette in the style of KSPlanner: dark slate panels with thin borders; the accent is set by the user.
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

/** Accent colours the user can pick (Settings > Appearance); blue by default, for the penguin. */
enum class Accent(val label: String, val dark: Long, val darkContainer: Long, val onDarkContainer: Long,
                  val light: Long, val lightContainer: Long, val onLightContainer: Long) {
    BLUE("Blue", 0xFF8EC5FF, 0xFF1F3A57, 0xFFD3E6FF, 0xFF2F6DAE, 0xFFD3E6FF, 0xFF0B2A4A),
    TEAL("Teal", 0xFF7FD4C8, 0xFF1C4A44, 0xFFC8F2EC, 0xFF1E7A6F, 0xFFC8F2EC, 0xFF0A3A34),
    AMBER("Amber", 0xFFF5C542, 0xFF4A3B0A, 0xFFFFE08A, 0xFF8A6500, 0xFFFFE08A, 0xFF2B2100),
    LAVENDER("Lavender", 0xFFC9B8FF, 0xFF3A3160, 0xFFE6DEFF, 0xFF6650B5, 0xFFE6DEFF, 0xFF241A4F),
    ROSE("Rose", 0xFFFFB0C0, 0xFF5A2B38, 0xFFFFD9E0, 0xFFB0405E, 0xFFFFD9E0, 0xFF3F0D1C);

    companion object {
        fun of(name: String?): Accent = entries.firstOrNull { it.name == name } ?: BLUE
    }
}

/** Second accent for KvK and Castle Battle: bar, background, text. */
data class Highlight(val bar: Color, val container: Color, val onContainer: Color)

@Composable
fun kvkHighlight(): Highlight = if (isSystemInDarkTheme())
    Highlight(Color(0xFFFF8A80), Color(0xFF4A2226), Color(0xFFFFDAD6))
else Highlight(Color(0xFFC62828), Color(0xFFFFDAD6), Color(0xFF410002))

@Composable
fun KsTheme(accent: Accent = Accent.BLUE, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) Dark.copy(
        primary = Color(accent.dark), onPrimary = Color(0xFF101418),
        primaryContainer = Color(accent.darkContainer), onPrimaryContainer = Color(accent.onDarkContainer),
        secondary = Color(accent.dark), onSecondary = Color(0xFF101418),
        secondaryContainer = Color(accent.darkContainer), onSecondaryContainer = Color(accent.onDarkContainer),
    ) else Light.copy(
        primary = Color(accent.light), onPrimary = Color.White,
        primaryContainer = Color(accent.lightContainer), onPrimaryContainer = Color(accent.onLightContainer),
        secondary = Color(accent.light), onSecondary = Color.White,
        secondaryContainer = Color(accent.lightContainer), onSecondaryContainer = Color(accent.onLightContainer),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
