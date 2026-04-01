package com.wallet.manager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF3056D3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFD7F4EF),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F2F8),
    surfaceVariant = Color(0xFFE7EAF4),
    onSurfaceVariant = Color(0xFF5B6275),
    outline = Color(0xFF8E96AA),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EB3FF),
    onPrimary = Color(0xFF0E1A47),
    primaryContainer = Color(0xFF1F2D63),
    secondary = Color(0xFF7AD8CD),
    secondaryContainer = Color(0xFF163A37),
    tertiary = Color(0xFFFFB86E),
    background = Color(0xFF101218),
    surface = Color(0xFF171A22),
    surfaceContainerHigh = Color(0xFF202430),
    surfaceVariant = Color(0xFF2A3040),
    onSurfaceVariant = Color(0xFFB8C0D4),
    outline = Color(0xFF7A8297),
    error = Color(0xFFFF8A80)
)

private val WalletShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun WalletTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        shapes = WalletShapes,
        content = content
    )
}
