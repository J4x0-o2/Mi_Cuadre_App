package com.micuadre.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand palette. Dark is the primary look (night driving); light is a derived counterpart.
 * Dark values come from the approved mockup and must not change without design sign-off.
 */

// ---- Dark ----
private val DarkBackground = Color(0xFF0B0F0E)
private val DarkSurface = Color(0xFF151B19)
private val DarkSurfaceVariant = Color(0xFF1D2522)
private val DarkOutline = Color(0xFF2A3430)
private val DarkOutlineVariant = Color(0xFF222B28)
private val DarkOnSurface = Color(0xFFF2F5F3)
private val DarkOnSurfaceVariant = Color(0xFF9AA8A2)
private val DarkPrimary = Color(0xFF3DDC97)
private val DarkOnPrimary = Color(0xFF04140D)
private val DarkPrimaryContainer = Color(0xFF12291F)
private val DarkOnPrimaryContainer = Color(0xFF7BEBB8)
private val DarkGold = Color(0xFFE8C27A)
private val DarkGoldContainer = Color(0xFF2E2614)
private val DarkOnGoldContainer = Color(0xFFF0D29A)

// ---- Light ----
private val LightBackground = Color(0xFFF6F8F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE8EEEB)
private val LightOutline = Color(0xFF6F7F78)
private val LightOutlineVariant = Color(0xFFD3DCD8)
private val LightOnSurface = Color(0xFF0B1411)
private val LightOnSurfaceVariant = Color(0xFF4A5A54)
private val LightPrimary = Color(0xFF0A7A4D)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFCFF5E2)
private val LightOnPrimaryContainer = Color(0xFF04301D)
private val LightGold = Color(0xFF8A6410)
private val LightGoldContainer = Color(0xFFF8EBCB)
private val LightOnGoldContainer = Color(0xFF4D3600)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = Color(0xFF0A7A4D),
    secondary = Color(0xFF8FB8A6),
    onSecondary = Color(0xFF0B1411),
    secondaryContainer = Color(0xFF1D2B26),
    onSecondaryContainer = Color(0xFFC6E8D8),
    tertiary = DarkGold,
    onTertiary = Color(0xFF2A1F00),
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = DarkOnGoldContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = Color(0xFFF2F5F3),
    inverseOnSurface = Color(0xFF0B0F0E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2D0606),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFB4B4),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B3531),
    surfaceDim = DarkBackground,
    surfaceContainerLowest = Color(0xFF080B0A),
    surfaceContainerLow = Color(0xFF111716),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF242E2A)
)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = DarkPrimary,
    secondary = Color(0xFF3F6455),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBE4),
    onSecondaryContainer = Color(0xFF0E2A1F),
    tertiary = Color(0xFF8A6410),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightGoldContainer,
    onTertiaryContainer = LightOnGoldContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF1D2522),
    inverseOnSurface = Color(0xFFF2F5F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE0DD),
    onErrorContainer = Color(0xFF410E0B),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = LightSurface,
    surfaceDim = Color(0xFFDCE3E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F3),
    surfaceContainer = Color(0xFFEBF0EE),
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE0E7E4)
)

/**
 * Tokens that Material 3 has no role for: the gold "on trip" accent and the money color.
 */
@Immutable
data class MiCuadreColors(
    val gold: Color,
    val goldContainer: Color,
    val onGoldContainer: Color,
    val money: Color
)

val DarkMiCuadreColors = MiCuadreColors(
    gold = DarkGold,
    goldContainer = DarkGoldContainer,
    onGoldContainer = DarkOnGoldContainer,
    money = DarkPrimary
)

val LightMiCuadreColors = MiCuadreColors(
    gold = LightGold,
    goldContainer = LightGoldContainer,
    onGoldContainer = LightOnGoldContainer,
    money = LightPrimary
)

val LocalMiCuadreColors = staticCompositionLocalOf { DarkMiCuadreColors }
