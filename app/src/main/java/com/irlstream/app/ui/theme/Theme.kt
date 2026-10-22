package com.irlstream.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.R

/**
 * RealStreamer's visual language: a broadcast deck, not a chat app.
 *
 * Near-black instrument panels, warm paper text, acid signal green for live/ready
 * signal paths, and the icon's magenta reserved for ON AIR / record. Type is a
 * condensed grotesque for labels and headlines with IBM Plex Mono for anything the
 * operator needs to read as data.
 */
object IRL {
    val Ink = Color(0xFF070809)
    val InkRaised = Color(0xFF0B0D0F)
    val Panel = Color(0xFF101315)
    val PanelHigh = Color(0xFF171B1E)
    val Line = Color(0xFF23282B)
    val LineStrong = Color(0xFF394246)

    /** Signal / ready / active. */
    val Signal = Color(0xFFB9FF3B)
    val SignalDeep = Color(0xFF6C9E1D)
    val SignalFaint = Color(0x1FB9FF3B)

    /** On air / record. The launcher icon's magenta. */
    val Live = Color(0xFFFF2E63)
    val LiveFaint = Color(0x1FFF2E63)

    val Text = Color(0xFFF2EFE6)
    val TextSecondary = Color(0xFFB7B6AC)
    val TextMuted = Color(0xFF7B807C)
    val TextFaint = Color(0xFF54595B)

    val Danger = Color(0xFFFF5449)
    val DangerFaint = Color(0x1FFF5449)
    val Warning = Color(0xFFFFC24B)
    val Blue = Color(0xFF7BA7FF)
    val Violet = Color(0xFFC49BFF)

    val Paper = Text
    val White88 = Color(0xE0F2EFE6)
    val White60 = Color(0x99F2EFE6)

    // Compatibility aliases used across earlier components.
    val Neon = Signal
    val NeonDeep = SignalDeep
    val NeonFaint = SignalFaint
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_medium, FontWeight.SemiBold),
)

private val DeckColorScheme = darkColorScheme(
    primary = IRL.Signal,
    onPrimary = IRL.Ink,
    primaryContainer = IRL.SignalDeep,
    onPrimaryContainer = IRL.Text,
    secondary = IRL.Live,
    onSecondary = IRL.Text,
    secondaryContainer = IRL.LiveFaint,
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
    inversePrimary = IRL.SignalDeep,
    scrim = Color.Black,
)

private val DeckTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.3.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.8.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.6.sp,
    ),
)

private val DeckShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Monospace style for timecodes, bitrates, IDs and counters. */
val MonoStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.1.sp,
)

/** Condensed micro-label style, always used uppercase. */
val MicroStyle = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.7.sp,
)

@Composable
fun RealStreamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeckColorScheme,
        typography = DeckTypography,
        shapes = DeckShapes,
        content = content,
    )
}
