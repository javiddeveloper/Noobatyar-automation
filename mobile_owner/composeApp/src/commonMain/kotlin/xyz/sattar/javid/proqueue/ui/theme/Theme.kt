package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import xyz.sattar.javid.proqueue.core.state.AppThemeMode


private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerHigh = Color(0xFF252525), // رنگ تیره‌تر برای باتم‌بار در تم دارک
    outline = DarkBorder,
    error = ErrorColor
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerHigh = Color(0xFFF0F0F0), // رنگ روشن متمایز برای باتم‌بار در تم لایت
    outline = LightBorder,
    error = ErrorColor
)


/**
 * Whether the dark scheme is active.
 *
 * AppTheme already knows this exactly, but never published it, so components
 * needing a light/dark branch resorted to guessing from the luminance of
 * `colorScheme.surface`. That works until a scheme changes and is impossible
 * to grep for; this is the same answer, stated once at the source.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    SystemAppearance(!isDarkTheme)

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalIsDarkTheme provides isDarkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            content = content
        )
    }
}

@Composable
expect fun SystemAppearance(isDark: Boolean)
