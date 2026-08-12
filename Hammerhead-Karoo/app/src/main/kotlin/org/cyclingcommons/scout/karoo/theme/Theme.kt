package org.cyclingcommons.scout.karoo.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScoutRed = Color(0xFFD1421F)

private val LightColors =
    lightColorScheme(
        primary = ScoutRed,
        onPrimary = Color.White,
        background = Color(0xFFF5F5F5),
        onBackground = Color(0xFF1A1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = ScoutRed,
        onPrimary = Color.White,
    )

@Composable
fun ScoutKarooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
