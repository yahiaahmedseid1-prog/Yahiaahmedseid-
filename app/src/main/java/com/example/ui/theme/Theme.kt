package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = SleekPrimary,
    onPrimary = Color.White,
    secondary = SleekSelfBubble,
    onSecondary = SleekSelfText,
    background = SleekBackground,
    surface = SleekSurface,
    onBackground = SleekOnSurface,
    onSurface = SleekOnSurface,
    surfaceVariant = SleekSurfaceVariant,
    onSurfaceVariant = SleekOnSurfaceVariant
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekPrimary,
    onPrimary = Color.White,
    secondary = SleekSelfBubble,
    onSecondary = SleekSelfText,
    background = Color(0xFF121316),
    surface = Color(0xFF1E2024),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFC4C6D0)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so our custom design is preserved
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
