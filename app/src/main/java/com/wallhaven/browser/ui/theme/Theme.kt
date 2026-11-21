package com.wallhaven.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌。深色/浅色各一套，默认跟随系统；App 内所有颜色都从这里取。
 */
@Immutable
data class WhTokens(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val field: Color,
    val sheet: Color,
    val glassTint: Color,
    val glassBackground: Color,
    val glassOverlay: Color,
    val glassCardOverlay: Color,
    val glassCardBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val hairline: Color,
    val accent: Color,
    val accentText: Color,
    val pink: Color,
    val green: Color,
    val orange: Color,
    val purple: Color,
    val chipBackground: Color,
    val chipBorder: Color,
    val overlayButtonBackground: Color,
    val overlayButtonBorder: Color,
    val overlayButtonIcon: Color,
    val tabPill: Color,
    val specularTop: Color,
    val borderTop: Color,
    val borderMid: Color,
    val borderBottom: Color,
    val switchTrack: Color,
)

private val DarkTokens = WhTokens(
    background = Color(0xFF08080B),
    surface = Color(0xFF141419),
    surfaceAlt = Color(0xFF1C1C23),
    field = Color(0xFF15151B),
    sheet = Color(0xFF121218),
    glassTint = Color(0x16FFFFFF),
    glassBackground = Color(0xE608080B),
    glassOverlay = Color(0x0D000000),
    glassCardOverlay = Color(0x26000000),
    glassCardBackground = Color(0xE60A0A0F),
    label = Color(0xFFF2F2F7),
    secondaryLabel = Color(0xFF9C9CA8),
    tertiaryLabel = Color(0xFF6C6C77),
    separator = Color(0x14FFFFFF),
    hairline = Color(0x1FFFFFFF),
    accent = Color(0xFF0A84FF),
    accentText = Color(0xFF64D2FF),
    pink = Color(0xFFFF375F),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    purple = Color(0xFFBF5AF2),
    chipBackground = Color(0x14FFFFFF),
    chipBorder = Color(0x1AFFFFFF),
    overlayButtonBackground = Color(0x66000000),
    overlayButtonBorder = Color(0x38FFFFFF),
    overlayButtonIcon = Color(0xFFFFFFFF),
    tabPill = Color(0x1AFFFFFF),
    specularTop = Color(0x16FFFFFF),
    borderTop = Color(0x5CFFFFFF),
    borderMid = Color(0x12FFFFFF),
    borderBottom = Color(0x38FFFFFF),
    switchTrack = Color(0xFF2C2C33),
)

private val LightTokens = WhTokens(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFEFF4),
    field = Color(0xFFFFFFFF),
    sheet = Color(0xFFFDFDFE),
    glassTint = Color(0x9EFFFFFF),
    glassBackground = Color(0xF2F7F7FB),
    glassOverlay = Color(0x80FFFFFF),
    glassCardOverlay = Color(0xF0FAFAFC),
    glassCardBackground = Color(0xEFFFFFFF),
    label = Color(0xFF0B0B10),
    secondaryLabel = Color(0xFF6C6C75),
    tertiaryLabel = Color(0xFF9A9AA4),
    separator = Color(0x14000000),
    hairline = Color(0x1F000000),
    accent = Color(0xFF007AFF),
    accentText = Color(0xFF007AFF),
    pink = Color(0xFFFF2D55),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    purple = Color(0xFFAF52DE),
    chipBackground = Color(0x0D000000),
    chipBorder = Color(0x14000000),
    overlayButtonBackground = Color(0xF2FFFFFF),
    overlayButtonBorder = Color(0x24000000),
    overlayButtonIcon = Color(0xFF0B0B10),
    tabPill = Color(0x1F000000),
    specularTop = Color(0x8CFFFFFF),
    borderTop = Color(0x33FFFFFF),
    borderMid = Color(0x14000000),
    borderBottom = Color(0x1A000000),
    switchTrack = Color(0xFFE4E4E9),
)

val LocalWhTokens = compositionLocalOf { DarkTokens }

/** 旧代码里的 Pal.XXX 引用保持不变，但现在会随主题（浅色/深色）切换。 */
object Pal {
    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.background
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.surface
    val SurfaceAlt: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.surfaceAlt
    val Field: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.field
    val Sheet: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.sheet
    val GlassTint: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.glassTint
    val GlassBackground: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.glassBackground
    val GlassCard: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.glassCardBackground
    val GlassOverlay: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.glassOverlay
    val GlassCardOverlay: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.glassCardOverlay
    val Label: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.label
    val LabelSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.secondaryLabel
    val LabelTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.tertiaryLabel
    val Separator: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.separator
    val Hairline: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.hairline
    val Accent: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.accent
    val AccentSoft: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.accentText
    val Pink: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.pink
    val Green: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.green
    val Orange: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.orange
    val Purple: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.purple
    val ChipBackground: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.chipBackground
    val ChipBorder: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.chipBorder
    val OverlayButtonBackground: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.overlayButtonBackground
    val OverlayButtonBorder: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.overlayButtonBorder
    val OverlayButtonIcon: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.overlayButtonIcon
    val TabPill: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.tabPill
    val SpecularTop: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.specularTop
    val BorderTop: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.borderTop
    val BorderMid: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.borderMid
    val BorderBottom: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.borderBottom
    val SwitchTrack: Color
        @Composable @ReadOnlyComposable get() = LocalWhTokens.current.switchTrack
}

private fun lightScheme(t: WhTokens) = lightColorScheme(
    primary = t.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00315C),
    secondary = t.accentText,
    onSecondary = Color.White,
    tertiary = t.pink,
    background = t.background,
    onBackground = t.label,
    surface = t.surface,
    onSurface = t.label,
    surfaceVariant = t.surfaceAlt,
    onSurfaceVariant = t.secondaryLabel,
    outline = Color(0xFFC7C7CC),
    outlineVariant = t.separator,
    error = Color(0xFFD70015),
)

private fun darkScheme(t: WhTokens) = darkColorScheme(
    primary = t.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2A4D),
    onPrimaryContainer = Color(0xFFCFE6FF),
    secondary = t.accentText,
    onSecondary = Color(0xFF06202B),
    tertiary = t.pink,
    background = t.background,
    onBackground = t.label,
    surface = t.surface,
    onSurface = t.label,
    surfaceVariant = t.surfaceAlt,
    onSurfaceVariant = t.secondaryLabel,
    outline = Color(0xFF3A3A44),
    outlineVariant = t.separator,
    error = Color(0xFFFF6B6B),
)

private val IosTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

object AppShapes {
    val card = RoundedCornerShape(22.dp)
    val chip = CircleShape
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val circle = CircleShape
}

/**
 * 默认跟随系统深浅色；[darkTheme] 可强制指定。
 */
@Composable
fun WallhavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    val scheme = if (darkTheme) darkScheme(tokens) else lightScheme(tokens)
    androidx.compose.runtime.CompositionLocalProvider(LocalWhTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IosTypography,
            shapes = IosShapes,
            content = content,
        )
    }
}
