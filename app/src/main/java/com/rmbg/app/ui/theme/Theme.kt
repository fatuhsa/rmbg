package com.rmbg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Indigo60,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SlateOnContainer,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = TealOnContainer,
    background = Background,
    surface = Background,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF444653),
    outline = Color(0xFF767889),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = IndigoOnContainer,
    primaryContainer = IndigoContainer80,
    onPrimaryContainer = IndigoContainer,
    secondary = Slate80,
    onSecondary = Color(0xFF1C333E),
    secondaryContainer = SlateContainer80,
    onSecondaryContainer = SlateContainer,
    tertiary = Teal80,
    onTertiary = Color(0xFF003D36),
    tertiaryContainer = TealContainer80,
    onTertiaryContainer = TealContainer,
    background = BackgroundDark,
    surface = BackgroundDark,
    onBackground = Color(0xFFE5E1E6),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC5C6D3),
    outline = Color(0xFF8F909D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun RMBGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
