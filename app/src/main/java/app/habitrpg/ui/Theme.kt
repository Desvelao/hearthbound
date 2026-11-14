package app.habitrpg.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.habitrpg.data.repo.ThemeMode

/**
 * Colours follow the pixel art: a navy and blue base with gold as the accent. `surfaceVariant` and the surface
 * containers are deliberately different, otherwise Material picks the muted text colour for text on cards.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2C63A8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FA), onPrimaryContainer = Color(0xFF0B2A4F),
    secondary = Color(0xFF7A5C00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE9B0), onSecondaryContainer = Color(0xFF2B2000),
    tertiary = Color(0xFF3F8CE0), onTertiary = Color.White,
    background = Color(0xFFF8F8FC), onBackground = Color(0xFF1B1B26),
    surface = Color(0xFFF8F8FC), onSurface = Color(0xFF1B1B26),
    surfaceVariant = Color(0xFFE3E4EE), onSurfaceVariant = Color(0xFF454659),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F8),
    surfaceContainer = Color(0xFFECECF4), surfaceContainerHigh = Color(0xFFE6E6EF), surfaceContainerHighest = Color(0xFFE0E0EA),
    outline = Color(0xFF6E6F82), outlineVariant = Color(0xFFC6C7D4),
    error = Color(0xFFB3261E), onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FBBF5), onPrimary = Color(0xFF0B2A4F),
    primaryContainer = Color(0xFF1F4A82), onPrimaryContainer = Color(0xFFD6E6FA),
    secondary = Color(0xFFF4C95D), onSecondary = Color(0xFF3B2B00),
    secondaryContainer = Color(0xFF574200), onSecondaryContainer = Color(0xFFFBE9B0),
    tertiary = Color(0xFF8FBBF5), onTertiary = Color(0xFF0B2A4F),
    background = Color(0xFF14151F), onBackground = Color(0xFFE4E4EE),
    surface = Color(0xFF14151F), onSurface = Color(0xFFE4E4EE),
    surfaceVariant = Color(0xFF454659), onSurfaceVariant = Color(0xFFC6C7D4),
    surfaceContainerLowest = Color(0xFF0E0F18), surfaceContainerLow = Color(0xFF1A1B27),
    surfaceContainer = Color(0xFF1E1F2C), surfaceContainerHigh = Color(0xFF282938), surfaceContainerHighest = Color(0xFF33344A),
    outline = Color(0xFF9899AA), outlineVariant = Color(0xFF454659),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

/** Whether the app should be dark, given the user's choice. "System" follows the phone. */
@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun HabitTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
