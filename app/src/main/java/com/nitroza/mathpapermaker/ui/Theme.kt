package com.nitroza.mathpapermaker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NitroBlue = Color(0xFF168BFF)
private val DarkScheme = darkColorScheme(
    primary = NitroBlue,
    secondary = Color(0xFF8B5CF6),
    background = Color(0xFF090D14),
    surface = Color(0xFF111927)
)

@Composable
fun MathPaperMakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
