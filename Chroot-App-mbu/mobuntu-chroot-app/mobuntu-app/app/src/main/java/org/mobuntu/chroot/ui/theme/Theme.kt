package org.mobuntu.chroot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFFe8426a),
    secondary        = Color(0xFFa855f7),
    tertiary         = Color(0xFF4ade80),
    background       = Color(0xFF0a0a0f),
    surface          = Color(0xFF0d0d14),
    surfaceVariant   = Color(0xFF12121f),
    onPrimary        = Color(0xFFffffff),
    onSecondary      = Color(0xFFffffff),
    onBackground     = Color(0xFFe8e8f0),
    onSurface        = Color(0xFFc8c8e0),
    outline          = Color(0xFF444460),
    error            = Color(0xFFf87171),
)

@Composable
fun MobuutuChrootTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}
