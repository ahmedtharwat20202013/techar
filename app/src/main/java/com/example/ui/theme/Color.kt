package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Global state tracking dark theme mode so color getters can resolve correctly without Composable context restrictions
var isDarkThemeGlobal by mutableStateOf(false)

// Precise Material 3 style color values
val md_theme_light_primary = Color(0xFF4F46E5)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_background = Color(0xFFF8FAFC)
val md_theme_light_onBackground = Color(0xFF0F172A)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF0F172A)
val md_theme_light_outline = Color(0xFF94A3B8)
val md_theme_light_onSurfaceVariant = Color(0xFF334155)

val md_theme_dark_primary = Color(0xFF9CCAFF)
val md_theme_dark_onPrimary = Color(0xFF003258)
val md_theme_dark_background = Color(0xFF101417)
val md_theme_dark_onBackground = Color(0xFFE1E2E5)
val md_theme_dark_surface = Color(0xFF1C1C1E)
val md_theme_dark_onSurface = Color(0xFFE1E2E5)
val md_theme_dark_outline = Color(0xFF8E9194)
val md_theme_dark_onSurfaceVariant = Color(0xFFC4C7CC)
val md_theme_dark_error = Color(0xFFFFB4AB)

// Dynamic design tokens mapped to the main app interfaces for automatic adaptive support
val PrimaryDarkGreen: Color get() = if (isDarkThemeGlobal) md_theme_dark_onBackground else md_theme_light_onBackground
val PrimaryGreen: Color get() = if (isDarkThemeGlobal) md_theme_dark_primary else md_theme_light_primary
val AccentGreen = Color(0xFF06B6D4)
val LightBgGreen: Color get() = if (isDarkThemeGlobal) md_theme_dark_background else md_theme_light_background
val SoftBgGreen: Color get() = if (isDarkThemeGlobal) md_theme_dark_surface else Color(0xFFEEF2F6)

val TextDark: Color get() = if (isDarkThemeGlobal) md_theme_dark_onBackground else md_theme_light_onBackground
val TextGray: Color get() = if (isDarkThemeGlobal) md_theme_dark_onSurfaceVariant else md_theme_light_onSurfaceVariant

val SuccessGreen = Color(0xFF10B981)
val WarningOrange = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val SurfaceContainer: Color get() = if (isDarkThemeGlobal) md_theme_dark_surface else Color(0xFFF1F5F9)
val SurfaceLowest: Color get() = if (isDarkThemeGlobal) md_theme_dark_surface else Color(0xFFFFFFFF)