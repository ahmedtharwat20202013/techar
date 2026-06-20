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
    background = TextDark,
    surface = Color(0xFF1E211E),
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
    onSurfaceVariant = Color(0xFF374151), // Premium Slate-700 (Very high contrast)
    outline = Color(0xFF9CA3AF), // Premium Slate-400 (Clear, distinct borders)
    outlineVariant = Color(0xFFD1D5DB) // Premium Slate-300
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
