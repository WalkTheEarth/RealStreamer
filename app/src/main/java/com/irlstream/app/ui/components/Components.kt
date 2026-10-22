package com.irlstream.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.engine.DestinationStatus
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The shared deck components.
 *
 * Rules for this file: flat fills, one-pixel rules, cut corners, no capsules, and
 * data set in mono. Magenta means ON AIR; signal green means a live signal path.
 */

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(IRL.Ink)) {
        DeckGrid(Modifier.fillMaxSize())
        // The icon's magenta, run as a registration stripe down the edge of the deck.
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(IRL.Live.copy(alpha = 0.65f))
                .align(Alignment.CenterStart),
        )
        content()
    }
}

/** A faint instrument grid. Used behind empty space, never behind the program feed. */
@Composable
fun DeckGrid(
    modifier: Modifier = Modifier,
    cell: Dp = 44.dp,
    color: Color = IRL.Line.copy(alpha = 0.55f),
) {
    Canvas(modifier) {
        val step = cell.toPx().coerceAtLeast(1f)
        var x = 0f
        while (x <= size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                val shape = CutCornerShape(topEnd = 8.dp)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(shape)
                        .background(IRL.Panel)
                        .border(1.dp, IRL.Line, shape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = IRL.Text,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(IRL.Signal))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title.uppercase(Locale.US),
                        style = MaterialTheme.typography.titleLarge,
                        color = IRL.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IRL.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 13.dp, top = 2.dp),
                    )
                }
            }
            actions()
        }
        Hairline()
    }
}

@Composable
fun MicroLabel(text: String, color: Color = IRL.TextMuted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.US),
        style = MicroStyle,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    background: Color = IRL.Panel,
    borderColor: Color = IRL.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CutCornerShape(topEnd = 18.dp)
    Column(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(14.dp),
        content = content,
    )
}

/** Deck plate with an optional frame index: 03 — OUTPUTS —————— */
@Composable
fun SectionHeader(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index.isNotBlank()) {
            Text(
                index,
                style = MonoStyle.copy(fontSize = 10.sp),
                color = IRL.Signal,
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.width(1.dp).height(11.dp).background(IRL.LineStrong))
            Spacer(Modifier.width(8.dp))
        } else {
            Box(Modifier.size(5.dp).background(IRL.Signal))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge,
            color = IRL.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Hairline(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    SectionHeader("", text, modifier, trailing)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val shape = CutCornerShape(topEnd = 14.dp)
    val bg = when {
        !enabled -> IRL.PanelHigh
        danger -> IRL.Live
        else -> IRL.Signal
    }
    val fg = when {
        !enabled -> IRL.TextFaint
        danger -> IRL.Text
        else -> IRL.Ink
    }
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
            color = fg,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = IRL.Text,
) {
    val shape = CutCornerShape(topEnd = 8.dp)
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(IRL.InkRaised)
            .border(1.dp, IRL.LineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else IRL.TextFaint,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
            color = if (enabled) tint else IRL.TextFaint,
        )
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = IRL.TextSecondary,
    background: Color = IRL.Panel,
    border: Boolean = true,
    size: Int = 38,
    iconSize: Int = 18,
) {
    val shape = CutCornerShape(topEnd = 8.dp)
    Box(
        modifier
            .size(size.dp)
            .clip(shape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, IRL.Line, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = IRL.TextSecondary,
    modifier: Modifier = Modifier,
) {
    val fg = when {
        !enabled -> IRL.TextFaint
        active -> IRL.Signal
        else -> tint
    }
    Column(
        modifier
            .height(54.dp)
            .clip(CutCornerShape(topEnd = 8.dp))
            .background(if (active) IRL.SignalFaint else Color.Transparent)
            .drawBehind {
                if (active) {
                    drawRect(
                        color = IRL.Signal,
                        topLeft = Offset(0f, size.height - 2.dp.toPx()),
                        size = Size(size.width, 2.dp.toPx()),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            label.uppercase(Locale.US),
            style = MicroStyle.copy(fontSize = 8.sp, letterSpacing = 0.9.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(
    status: DestinationStatus,
    size: Int = 7,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        DestinationStatus.LIVE -> IRL.Signal
        DestinationStatus.CONNECTING, DestinationStatus.RECONNECTING -> IRL.Warning
        DestinationStatus.ERROR -> IRL.Danger
        DestinationStatus.DISABLED -> IRL.TextFaint
        DestinationStatus.OFFLINE -> IRL.TextMuted
    }
    val animated = status == DestinationStatus.LIVE ||
        status == DestinationStatus.CONNECTING ||
        status == DestinationStatus.RECONNECTING
    val alpha = if (animated) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else {
        1f
    }
    Box(
        modifier
            .size(size.dp)
            .background(color.copy(alpha = alpha))
            .border(1.dp, color.copy(alpha = 0.35f)),
    )
}

/** Broadcast tally: a hard plate with a tally bar, a square lamp, and a timecode. */
@Composable
fun LiveBadge(
    live: Boolean,
    durationMs: Long,
    idleLabel: String = "STANDBY",
    modifier: Modifier = Modifier,
) {
    val color = if (live) IRL.Live else IRL.Signal
    val shape = CutCornerShape(topEnd = 8.dp)
    val transition = rememberInfiniteTransition(label = "live")
    val pulse = if (live) {
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "livePulse",
        ).value
    } else {
        1f
    }
    Row(
        modifier
            .height(26.dp)
            .clip(shape)
            .background(if (live) IRL.LiveFaint else IRL.SignalFaint)
            .border(1.dp, color.copy(alpha = 0.5f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .size(6.dp)
                .background(color.copy(alpha = pulse)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (live) "ON AIR" else idleLabel.uppercase(Locale.US),
            style = MicroStyle.copy(fontSize = 9.sp, letterSpacing = 1.4.sp),
            color = color,
        )
        if (live) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(11.dp).background(color.copy(alpha = 0.35f)))
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(durationMs),
                style = MonoStyle.copy(fontSize = 10.sp),
                color = color,
            )
        }
        Spacer(Modifier.width(9.dp))
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = CutCornerShape(topEnd = 6.dp)
    val fg = when {
        selected -> IRL.Ink
        enabled -> IRL.TextSecondary
        else -> IRL.TextFaint
    }
    Row(
        modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) IRL.Signal else IRL.Panel)
            .border(1.dp, if (selected) IRL.Signal else IRL.LineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CutCornerShape(topEnd = 8.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(IRL.InkRaised)
            .border(1.dp, IRL.Line, shape),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) IRL.PanelHigh else Color.Transparent)
                    .drawBehind {
                        if (isSelected) {
                            drawRect(
                                IRL.Signal,
                                topLeft = Offset(0f, size.height - 2.dp.toPx()),
                                size = Size(size.width, 2.dp.toPx()),
                            )
                        }
                        if (index < options.lastIndex) {
                            drawLine(
                                IRL.Line,
                                Offset(size.width, 0f),
                                Offset(size.width, size.height),
                                1.dp.toPx(),
                            )
                        }
                    }
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) IRL.Signal else IRL.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Square, hard-edged toggle. The thumb is a slug, not a ball. */
@Composable
fun DeckToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CutCornerShape(topEnd = 6.dp)
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        label = "deckToggleThumb",
    ).value
    Box(
        modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(shape)
            .background(if (checked) IRL.Signal else IRL.PanelHigh)
            .border(1.dp, if (checked) IRL.Signal else IRL.LineStrong, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(16.dp)
                .background(if (checked) IRL.Ink else IRL.TextMuted),
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IRL.TextMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            DeckToggle(checked = checked, onCheckedChange = onCheckedChange)
        }
        Hairline()
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) IRL.Text else IRL.TextFaint,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IRL.TextMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = if (enabled) IRL.Signal else IRL.TextFaint,
                )
                Spacer(Modifier.width(9.dp))
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = IRL.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        Hairline()
    }
}

/**
 * A broadcast fader: engraved track, square ticks, and a hard slug you can drag.
 * Kept API-compatible with the old Material slider.
 */
@Composable
fun IRLSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = range.start
    val end = range.endInclusive
    val span = (end - start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - start) / span).coerceIn(0f, 1f)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val tickShape = CutCornerShape(topEnd = 4.dp)

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(Locale.US),
                style = MicroStyle.copy(fontSize = 10.sp, letterSpacing = 1.15.sp),
                color = IRL.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(tickShape)
                    .background(IRL.SignalFaint)
                    .border(1.dp, IRL.Signal.copy(alpha = 0.3f), tickShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    valueLabel,
                    style = MonoStyle.copy(fontSize = 10.sp),
                    color = IRL.Signal,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(start, end, steps) {
                    val inset = 9.dp.toPx()
                    fun emit(x: Float) {
                        val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
                        val f = ((x - inset) / trackWidth).coerceIn(0f, 1f)
                        var next = start + f * span
                        if (steps > 0) {
                            val stepSize = span / (steps + 1)
                            next = start + ((next - start) / stepSize).roundToInt() * stepSize
                        }
                        latestOnValueChange.value(next.coerceIn(start, end))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        emit(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            emit(change.position.x)
                            change.consume()
                        }
                    }
                },
        ) {
            val inset = 9.dp.toPx()
            val cy = size.height / 2f
            val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
            val tickCount = if (steps > 0) steps + 2 else 9
            repeat(tickCount.coerceAtLeast(2)) { index ->
                val x = inset + trackWidth * index / (tickCount.coerceAtLeast(2) - 1).toFloat()
                drawLine(
                    color = IRL.LineStrong,
                    start = Offset(x, cy - 10.dp.toPx()),
                    end = Offset(x, cy - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawLine(
                IRL.LineStrong,
                Offset(inset, cy),
                Offset(inset + trackWidth, cy),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                IRL.Signal,
                Offset(inset, cy),
                Offset(inset + trackWidth * fraction, cy),
                strokeWidth = 2.dp.toPx(),
            )
            val cx = inset + trackWidth * fraction
            val thumbWidth = 12.dp.toPx()
            val thumbHeight = 20.dp.toPx()
            val thumbTop = cy - thumbHeight / 2f
            drawRect(
                color = IRL.Ink,
                topLeft = Offset(cx - thumbWidth / 2f, thumbTop),
                size = Size(thumbWidth, thumbHeight),
            )
            drawRect(
                color = IRL.Signal,
                topLeft = Offset(cx - thumbWidth / 2f, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawLine(
                IRL.Signal,
                Offset(cx, cy - 4.dp.toPx()),
                Offset(cx, cy + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
fun TextFieldIRL(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IRL.TextFaint,
                )
            }
        },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = CutCornerShape(topEnd = 10.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = IRL.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = IRL.Text,
            unfocusedTextColor = IRL.Text,
            focusedContainerColor = IRL.InkRaised,
            unfocusedContainerColor = IRL.InkRaised,
            focusedBorderColor = IRL.Signal,
            unfocusedBorderColor = IRL.LineStrong,
            errorBorderColor = IRL.Danger,
            cursorColor = IRL.Signal,
            focusedLabelColor = IRL.Signal,
            unfocusedLabelColor = IRL.TextMuted,
            focusedPlaceholderColor = IRL.TextFaint,
            unfocusedPlaceholderColor = IRL.TextFaint,
            errorSupportingTextColor = IRL.Danger,
            focusedSupportingTextColor = IRL.TextMuted,
            unfocusedSupportingTextColor = IRL.TextMuted,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetContent(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IRL.InkRaised,
        contentColor = IRL.Text,
        scrimColor = Color.Black.copy(alpha = 0.76f),
        shape = CutCornerShape(topStart = 22.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 3.dp)
                        .background(IRL.LineStrong),
                )
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(IRL.Signal))
                Spacer(Modifier.width(8.dp))
                MicroLabel("RealStreamer / Config", IRL.TextMuted)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                title.uppercase(Locale.US),
                style = MaterialTheme.typography.headlineMedium,
                color = IRL.Text,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IRL.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = CutCornerShape(topEnd = 12.dp)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(shape)
                    .background(IRL.Panel)
                    .border(1.dp, IRL.Line, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = IRL.Signal, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                MicroLabel("Not configured", IRL.TextFaint)
                Spacer(Modifier.height(2.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = IRL.Text,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = IRL.TextMuted,
            modifier = Modifier.fillMaxWidth(0.94f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = IRL.Signal,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val maxValue = max(values.maxOrNull() ?: 1f, 1f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(
                IRL.Line.copy(alpha = 0.7f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value / maxValue).coerceIn(0f, 1f)
            val y = size.height - normalized * size.height * 0.84f - 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)),
        )
        drawPath(
            path,
            color = color,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Square),
        )
        val lastY = size.height - (values.last() / maxValue).coerceIn(0f, 1f) * size.height * 0.84f - 2.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(size.width - 3.dp.toPx(), lastY - 3.dp.toPx() / 2f),
            size = Size(3.dp.toPx(), 3.dp.toPx()),
        )
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val segments = 24
    val lit = (level.coerceIn(0f, 1f) * segments).toInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(segments) { index ->
            val active = index < lit
            val color = when {
                index >= segments * 0.88f -> IRL.Danger
                index >= segments * 0.70f -> IRL.Warning
                else -> IRL.Signal
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(if (active) color else IRL.Line),
            )
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = IRL.Warning,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topEnd = 8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), CutCornerShape(topEnd = 8.dp))
            .drawBehind {
                drawRect(
                    color = color,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 12.dp, end = 11.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = IRL.TextSecondary,
        )
    }
}

/** Hard plate for labels like PROGRAM / LOCAL ONLY / WYSIWYG. */
@Composable
fun DeckPlate(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IRL.TextSecondary,
    filled: Boolean = false,
) {
    val shape = CutCornerShape(topEnd = 4.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text.uppercase(Locale.US),
            style = MicroStyle.copy(fontSize = 8.sp, letterSpacing = 1.3.sp),
            color = if (filled) IRL.Ink else color,
        )
    }
}

/** Four corner marks, for framing the program feed. */
@Composable
fun CornerBrackets(
    modifier: Modifier = Modifier,
    color: Color = IRL.Text.copy(alpha = 0.5f),
    inset: Dp = 10.dp,
    length: Dp = 16.dp,
) {
    Canvas(modifier) {
        val i = inset.toPx()
        val l = length.toPx()
        val w = 1.dp.toPx()
        val right = size.width - i
        val bottom = size.height - i
        drawLine(color, Offset(i, i), Offset(i + l, i), w)
        drawLine(color, Offset(i, i), Offset(i, i + l), w)
        drawLine(color, Offset(right, i), Offset(right - l, i), w)
        drawLine(color, Offset(right, i), Offset(right, i + l), w)
        drawLine(color, Offset(i, bottom), Offset(i + l, bottom), w)
        drawLine(color, Offset(i, bottom), Offset(i, bottom - l), w)
        drawLine(color, Offset(right, bottom), Offset(right - l, bottom), w)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - l), w)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = IRL.Line)
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatBitrate(kbps: Int): String =
    if (kbps >= 1000) String.format(Locale.US, "%.1f Mbps", kbps / 1000f)
    else "$kbps kbps"
