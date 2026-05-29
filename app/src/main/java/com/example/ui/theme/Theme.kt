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
    primary = EcoPrimaryDark,
    onPrimary = EcoBackgroundDark,
    primaryContainer = EcoPrimaryContainerDark,
    onPrimaryContainer = BrandWhite,
    secondary = EcoSecondaryDark,
    onSecondary = EcoBackgroundDark,
    secondaryContainer = Color(0xFF232D20),
    onSecondaryContainer = BrandGrey,
    tertiary = EcoTertiaryDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3E2812),
    onTertiaryContainer = BrandOrange,
    background = EcoBackgroundDark,
    onBackground = EcoOnBackgroundDark,
    surface = EcoSurfaceDark,
    onSurface = EcoOnSurfaceDark,
    surfaceVariant = Color(0xFF2C3928),
    onSurfaceVariant = BrandGrey,
    outline = BrandSage,
    outlineVariant = Color(0xFF4C5E43),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFFB00020),
    onErrorContainer = BrandWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EcoPrimaryLight,
    onPrimary = BrandWhite,
    primaryContainer = EcoPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF1A2417),
    secondary = EcoSecondaryLight,
    onSecondary = BrandWhite,
    secondaryContainer = Color(0xFFEAECE9),
    onSecondaryContainer = Color(0xFF1A2417),
    tertiary = EcoTertiaryLight,
    onTertiary = BrandWhite,
    tertiaryContainer = Color(0xFFFFEAD2),
    onTertiaryContainer = Color(0xFFC75D00),
    background = EcoBackgroundLight,
    onBackground = EcoOnBackgroundLight,
    surface = EcoSurfaceLight,
    onSurface = EcoOnSurfaceLight,
    surfaceVariant = Color(0xFFE2E5E1),
    onSurfaceVariant = Color(0xFF4C5E43),
    outline = BrandGreen,
    outlineVariant = BrandSage,
    error = Color(0xFFB00020),
    onError = BrandWhite,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF9B1C1C)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve custom sleek branding
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
