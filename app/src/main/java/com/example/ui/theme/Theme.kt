package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppThemeMode

@Immutable
data class BcExtraColors(
  val whatsappBg: Color,
  val whatsappOwnBubble: Color,
  val whatsappOtherBubble: Color,
  val whatsappSystemChip: Color,
  val whatsappHeaderBg: Color,
  val successGreen: Color,
  val successGreenContainer: Color,
  val warningAmber: Color,
  val warningAmberContainer: Color,
  val liveRed: Color,
  val celebrationGold: Color
)

val LocalBcExtraColors = staticCompositionLocalOf {
  BcExtraColors(
    whatsappBg = Color(0xFFEFEAE2),
    whatsappOwnBubble = Color(0xFFD9FDD3),
    whatsappOtherBubble = Color(0xFFFFFFFF),
    whatsappSystemChip = Color(0xFFE1F3FF),
    whatsappHeaderBg = Color(0xFF075E54),
    successGreen = Color(0xFF15803D),
    successGreenContainer = Color(0xFFDCFCE7),
    warningAmber = Color(0xFFB45309),
    warningAmberContainer = Color(0xFFFEF3C7),
    liveRed = Color(0xFFDC2626),
    celebrationGold = Color(0xFFF59E0B)
  )
}

private val RoyalBlueScheme = lightColorScheme(
  primary = RoyalBluePrimary,
  onPrimary = RoyalBlueOnPrimary,
  primaryContainer = RoyalBluePrimaryContainer,
  onPrimaryContainer = RoyalBlueOnPrimaryContainer,
  secondary = RoyalBlueSecondary,
  onSecondary = RoyalBlueOnSecondary,
  secondaryContainer = RoyalBlueSecondaryContainer,
  onSecondaryContainer = RoyalBlueOnSecondaryContainer,
  background = RoyalBlueBackground,
  onBackground = RoyalBlueOnSurface,
  surface = RoyalBlueSurface,
  onSurface = RoyalBlueOnSurface,
  surfaceVariant = RoyalBlueSurfaceVariant,
  onSurfaceVariant = RoyalBlueOnSurfaceVariant,
  outline = RoyalBlueOutline
)

private val EmeraldGreenScheme = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = EmeraldOnPrimary,
  primaryContainer = EmeraldPrimaryContainer,
  onPrimaryContainer = EmeraldOnPrimaryContainer,
  secondary = EmeraldSecondary,
  onSecondary = EmeraldOnSecondary,
  secondaryContainer = EmeraldSecondaryContainer,
  onSecondaryContainer = EmeraldOnSecondaryContainer,
  background = EmeraldBackground,
  onBackground = EmeraldOnSurface,
  surface = EmeraldSurface,
  onSurface = EmeraldOnSurface,
  surfaceVariant = EmeraldSurfaceVariant,
  onSurfaceVariant = EmeraldOnSurfaceVariant,
  outline = EmeraldOutline
)

private val DarkModeScheme = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = DarkOnSecondary,
  secondaryContainer = DarkSecondaryContainer,
  onSecondaryContainer = DarkOnSecondaryContainer,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline
)

@Composable
fun HamariBcTheme(
  themeMode: AppThemeMode = AppThemeMode.ROYAL_BLUE,
  content: @Composable () -> Unit
) {
  val isDark = themeMode == AppThemeMode.DARK_MODE
  val colorScheme = when (themeMode) {
    AppThemeMode.ROYAL_BLUE -> RoyalBlueScheme
    AppThemeMode.EMERALD_GREEN -> EmeraldGreenScheme
    AppThemeMode.DARK_MODE -> DarkModeScheme
  }

  val extraColors = when (themeMode) {
    AppThemeMode.ROYAL_BLUE -> BcExtraColors(
      whatsappBg = Color(0xFFECE6DC),
      whatsappOwnBubble = Color(0xFFD9FDD3),
      whatsappOtherBubble = Color(0xFFFFFFFF),
      whatsappSystemChip = Color(0xFFDCE6FF),
      whatsappHeaderBg = Color(0xFF1A47B8),
      successGreen = Color(0xFF15803D),
      successGreenContainer = Color(0xFFDCFCE7),
      warningAmber = Color(0xFFB45309),
      warningAmberContainer = Color(0xFFFEF3C7),
      liveRed = Color(0xFFDC2626),
      celebrationGold = Color(0xFFF59E0B)
    )
    AppThemeMode.EMERALD_GREEN -> BcExtraColors(
      whatsappBg = Color(0xFFEAECE4),
      whatsappOwnBubble = Color(0xFFD1FADF),
      whatsappOtherBubble = Color(0xFFFFFFFF),
      whatsappSystemChip = Color(0xFFD1FAE5),
      whatsappHeaderBg = Color(0xFF075E54),
      successGreen = Color(0xFF047857),
      successGreenContainer = Color(0xFFD1FAE5),
      warningAmber = Color(0xFFB45309),
      warningAmberContainer = Color(0xFFFEF3C7),
      liveRed = Color(0xFFDC2626),
      celebrationGold = Color(0xFFEAB308)
    )
    AppThemeMode.DARK_MODE -> BcExtraColors(
      whatsappBg = Color(0xFF0B141A),
      whatsappOwnBubble = Color(0xFF005C4B),
      whatsappOtherBubble = Color(0xFF202C33),
      whatsappSystemChip = Color(0xFF1E293B),
      whatsappHeaderBg = Color(0xFF1F2C34),
      successGreen = Color(0xFF4ADE80),
      successGreenContainer = Color(0xFF064E3B),
      warningAmber = Color(0xFFFBBF24),
      warningAmberContainer = Color(0xFF451A03),
      liveRed = Color(0xFFF87171),
      celebrationGold = Color(0xFFFBBF24)
    )
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
      }
    }
  }

  CompositionLocalProvider(LocalBcExtraColors provides extraColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content
    )
  }
}
