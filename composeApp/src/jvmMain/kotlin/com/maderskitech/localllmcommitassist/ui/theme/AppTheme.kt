package com.maderskitech.localllmcommitassist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF0F6CBD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E9FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF0A7C66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7F1E3),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF6B5BD2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF1E1149),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF12171F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12171F),
    surfaceVariant = Color(0xFFE9EFF5),
    onSurfaceVariant = Color(0xFF3B4758),
    outline = Color(0xFF6E7A8A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val AppTypography = Typography()

@Composable
fun LocalCommitAssistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
