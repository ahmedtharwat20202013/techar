package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Global state tracking dark theme mode so color getters can resolve correctly without Composable context restrictions
var isDarkThemeGlobal by mutableStateOf(false)

// Visual Design System specifications (Stitch-inspired Royal Indigo & Electric Cyan theme)
val PrimaryDarkGreen: Color get() = if (isDarkThemeGlobal) Color(0xFFF8FAFC) else Color(0xFF0F172A) // Deep Slate-900 (Stunning high-contrast headings & deep cards)
val PrimaryGreen = Color(0xFF4F46E5)     // Vibrant Indigo (Primary active actions & branded triggers)
val AccentGreen = Color(0xFF06B6D4)      // Electric Cyan (Playful status trackers & secondary highlights)
val LightBgGreen: Color get() = if (isDarkThemeGlobal) Color(0xFF0F172A) else Color(0xFFF8FAFC)     // Soft Cool Slate-50 (Very clean app background)
val SoftBgGreen: Color get() = if (isDarkThemeGlobal) Color(0xFF1E293B) else Color(0xFFEEF2F6)      // Lighter Slate-100 (Sleek container & search fill backgrounds)

val TextDark: Color get() = if (isDarkThemeGlobal) Color(0xFFF8FAFC) else Color(0xFF0F172A)         // Deep Slate-900 for modern readability
val TextGray: Color get() = if (isDarkThemeGlobal) Color(0xFF94A3B8) else Color(0xFF64748B)         // Slate-500 for neutral supporting text

val SuccessGreen = Color(0xFF10B981)     // Modern Emerald-550 for instant success and active triggers
val WarningOrange = Color(0xFFF59E0B)    // Amber-500 for warm financial alerts & notice states
val DangerRed = Color(0xFFEF4444)        // Vibrant Ruby Rose for overdue balances & delete actions
val SurfaceContainer: Color get() = if (isDarkThemeGlobal) Color(0xFF334155) else Color(0xFFF1F5F9) // Clean Slate-100 container lines
val SurfaceLowest: Color get() = if (isDarkThemeGlobal) Color(0xFF1E293B) else Color(0xFFFFFFFF)    // Crisp white for primary content surfaces
