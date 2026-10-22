package com.irlstream.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The RealStreamer palette: broadcast-console black with a single neon signal color. */
object IRL {
    val Ink = Color(0xFF050806)
    val InkRaised = Color(0xFF080D0A)
    val Panel = Color(0xFF0D1510)
    val PanelHigh = Color(0xFF121E17)
    val Line = Color(0xFF1C2B22)
    val LineStrong = Color(0xFF2C4435)

    val Neon = Color(0xFF39FF88)
    val NeonDeep = Color(0xFF12A85A)
    val NeonFaint = Color(0x1F39FF88)

    val Text = Color(0xFFF0FBF4)
    val TextSecondary = Color(0xFFA6B8AC)
    val TextMuted = Color(0xFF73857A)
    val TextFaint = Color(0xFF4C5B52)

    val Danger = Color(0xFFFF5E6E)
    val DangerFaint = Color(0x1FFF5E6E)
    val Warning = Color(0xFFFFC857)
    val Blue = Color(0xFF6EA8FF)
    val Violet = Color(0xFFB48CFF)

    val White88 = Color(0xE0FFFFFF)
    val White60 = Color(0x99FFFFFF)
}

private val IRLColorScheme = darkColorScheme(
    primary = IRL.Neon,
    onPrimary = IRL.Ink,
    primaryContainer = IRL.NeonDeep,
    onPrimaryContainer = IRL.Text,
    secondary = IRL.NeonDeep,
    onSecondary = IRL.Ink,
    secondaryContainer = IRL.PanelHigh,
    onSecondaryContainer = IRL.Text,
    tertiary = IRL.Blue,
    onTertiary = IRL.Ink,
    background = IRL.Ink,
    onBackground = IRL.Text,
    surface = IRL.InkRaised,
    onSurface = IRL.Text,
    surfaceVariant = IRL.Panel,
    onSurfaceVariant = IRL.TextSecondary,
    surfaceContainer = IRL.Panel,
    surfaceContainerHigh = IRL.PanelHigh,
    surfaceContainerHighest = IRL.PanelHigh,
    surfaceContainerLow = IRL.InkRaised,
    surfaceContainerLowest = IRL.Ink,
    outline = IRL.Line,
    outlineVariant = IRL.LineStrong,
    error = IRL.Danger,
    onError = IRL.Ink,
    errorContainer = IRL.DangerFaint,
    onErrorContainer = IRL.Danger,
    inverseSurface = IRL.Text,
    inverseOnSurface = IRL.Ink,
    inversePrimary = IRL.NeonDeep,
    scrim = Color.Black,
)

private val IRLTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
)

private val IRLShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Monospace style used for timecodes and bitrates. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp,
)

val MicroStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.6.sp,
)

@Composable
fun RealStreamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IRLColorScheme,
        typography = IRLTypography,
        shapes = IRLShapes,
        content = content,
    )
}
