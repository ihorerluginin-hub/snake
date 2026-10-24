package com.biologigor.snake.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0E141B)
val BoardA = Color(0xFF131E28)
val BoardB = Color(0xFF16212D)
val BoardBorder = Color(0xFF223041)
val SnakeHead = Color(0xFF8FF06A)
val SnakeTail = Color(0xFF2E7D32)
val Apple = Color(0xFFFF5252)
val Leaf = Color(0xFF8BC34A)
val Obstacle = Color(0xFF44536A)
val BonusRed = Color(0xFFFF2A2A)
val PadFace = Color(0xFF18232F)
val PadRim = Color(0xFF3D5568)
val TextMain = Color(0xFFE6EDF3)
val TextDim = Color(0xFF9AA7B4)
val Accent = Color(0xFF6FE24E)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1A0F),
    background = BgDark,
    onBackground = TextMain,
    surface = Color(0xFF161F2A),
    onSurface = TextMain,
    surfaceVariant = Color(0xFF1B2634),
    onSurfaceVariant = TextMain,
)

@Composable
fun SnakeTheme(content: @Composable () -> Unit) {
    // Тема всегда тёмная: это игра, светлой темы не нужно
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
