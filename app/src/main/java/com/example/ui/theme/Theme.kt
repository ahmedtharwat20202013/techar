package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = AccentGreen,
    secondary = PrimaryGreen,
    tertiary = PrimaryDarkGreen,
    background = Color(0xFF0F172A), // Premium dark slate background
    surface = Color(0xFF1E293B),    // Slate-800 container surface
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryGreen,
    secondary = PrimaryDarkGreen,
    tertiary = AccentGreen,
    background = LightBgGreen,
    surface = SurfaceLowest,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF334155), // Modern Slate-700 for high-contrast secondary texts
    outline = Color(0xFF94A3B8),         // Modern Slate-400 for neat outlines
    outlineVariant = Color(0xFFCBD5E1)   // Modern Slate-300 for soft dividers
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  // Dynamic color is disabled by default to maintain the gorgeous green signature branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        dynamicLightColorScheme(context)
      }

      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
