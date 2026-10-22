package com.irlstream.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.engine.DestinationStatus
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import java.util.Locale
import kotlin.math.max

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(IRL.Ink, Color(0xFF071009), IRL.Ink),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(IRL.Neon.copy(alpha = 0.05f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1f, -0.3f),
                        radius = 1600f,
                    ),
                ),
        )
        content()
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IRL.Panel)
                    .border(1.dp, IRL.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = IRL.Text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = IRL.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IRL.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
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
fun AntiLabel(text: String, color: Color = IRL.TextMuted, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MicroStyle.copy(letterSpacing = 0.2.sp),
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicroLabel(text, IRL.TextMuted, Modifier.weight(1f))
        trailing?.invoke()
    }
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
    val bg = when {
        !enabled -> IRL.PanelHigh
        danger -> IRL.Danger
        else -> IRL.Neon
    }
    val fg = when {
        !enabled -> IRL.TextFaint
        danger -> IRL.Ink
        else -> IRL.Ink
    }
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(IRL.Panel)
            .border(1.dp, IRL.LineStrong, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) tint else IRL.TextFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
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
    size: Int = 40,
    iconSize: Int = 20,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, IRL.Line, CircleShape) else Modifier)
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
        active -> IRL.Neon
        else -> tint
    }
    Column(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) IRL.NeonFaint else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MicroStyle.copy(fontSize = 8.sp, letterSpacing = 0.7.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(
    status: DestinationStatus,
    size: Int = 8,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        DestinationStatus.LIVE -> IRL.Neon
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
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
fun LiveBadge(
    live: Boolean,
    durationMs: Long,
    idleLabel: String = "READY",
    modifier: Modifier = Modifier,
) {
    val color = if (live) IRL.Danger else IRL.Neon
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
            .clip(RoundedCornerShape(50))
            .background(if (live) IRL.DangerFaint else IRL.NeonFaint)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulse)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (live) "LIVE" else idleLabel,
            style = MicroStyle,
            color = color,
        )
        if (live) {
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(durationMs),
                style = MonoStyle.copy(fontSize = 11.sp),
                color = color,
            )
        }
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
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) IRL.Neon else IRL.Panel)
            .border(
                1.dp,
                if (selected) IRL.Neon else IRL.Line,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) IRL.Ink else if (enabled) IRL.TextSecondary else IRL.TextFaint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) IRL.Ink else if (enabled) IRL.TextSecondary else IRL.TextFaint,
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
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IRL.Panel)
            .border(1.dp, IRL.Line, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) IRL.PanelHigh else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) IRL.Neon else IRL.TextMuted,
                )
            }
        }
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IRL.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IRL.Ink,
                checkedTrackColor = IRL.Neon,
                checkedBorderColor = IRL.Neon,
                uncheckedThumbColor = IRL.TextMuted,
                uncheckedTrackColor = IRL.PanelHigh,
                uncheckedBorderColor = IRL.LineStrong,
            ),
        )
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
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) IRL.Text else IRL.TextFaint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IRL.TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = IRL.TextSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = IRL.TextFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

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
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = IRL.Text, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MonoStyle.copy(fontSize = 12.sp), color = IRL.Neon)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = IRL.Neon,
                activeTrackColor = IRL.Neon,
                inactiveTrackColor = IRL.LineStrong,
                activeTickColor = IRL.Neon.copy(alpha = 0.5f),
                inactiveTickColor = IRL.LineStrong,
            ),
        )
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
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = IRL.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = IRL.Text,
            unfocusedTextColor = IRL.Text,
            focusedContainerColor = IRL.Panel,
            unfocusedContainerColor = IRL.Panel,
            focusedBorderColor = IRL.Neon,
            unfocusedBorderColor = IRL.Line,
            errorBorderColor = IRL.Danger,
            cursorColor = IRL.Neon,
            focusedLabelColor = IRL.Neon,
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
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
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
                .padding(bottom = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = IRL.Text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IRL.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
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
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IRL.Panel)
                .border(1.dp, IRL.Line, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = IRL.Neon, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = IRL.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = IRL.TextMuted,
            modifier = Modifier.fillMaxWidth(0.9f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = IRL.Neon,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val maxValue = max(values.maxOrNull() ?: 1f, 1f)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value / maxValue).coerceIn(0f, 1f)
            val y = size.height - normalized * size.height * 0.86f - 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), Color.Transparent)),
        )
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val segments = 22
    val lit = (level.coerceIn(0f, 1f) * segments).toInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { index ->
            val active = index < lit
            val color = when {
                index > segments * 0.86f -> IRL.Danger
                index > segments * 0.68f -> IRL.Warning
                else -> IRL.Neon
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(2.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.92f))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = IRL.Line)
}

@Composable
fun DragGrip(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Reorder",
        tint = IRL.TextFaint,
        modifier = modifier.size(18.dp),
    )
}

@Composable
fun SelectionCheck(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) IRL.Neon else Color.Transparent)
            .border(1.dp, if (selected) IRL.Neon else IRL.LineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = IRL.Ink, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = IRL.TextMuted)
    }
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

fun rotateModifier(degrees: Float): Modifier = Modifier.rotate(degrees)

@Composable
fun VerticalGlowDivider(color: Color = IRL.Neon) {
    val density = LocalDensity.current
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.5f), Color.Transparent),
                ),
            ),
    )
}
