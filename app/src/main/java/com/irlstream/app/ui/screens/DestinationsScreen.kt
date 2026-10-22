package com.irlstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.engine.DestinationStatus
import com.irlstream.app.engine.EngineState
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.ChoiceChip
import com.irlstream.app.ui.components.DeckToggle
import com.irlstream.app.ui.components.EmptyState
import com.irlstream.app.ui.components.Hairline
import com.irlstream.app.ui.components.IconAction
import com.irlstream.app.ui.components.InfoBanner
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PrimaryButton
import com.irlstream.app.ui.components.ScreenTopBar
import com.irlstream.app.ui.components.SecondaryButton
import com.irlstream.app.ui.components.SectionHeader
import com.irlstream.app.ui.components.SettingSwitchRow
import com.irlstream.app.ui.components.SheetContent
import com.irlstream.app.ui.components.StatusDot
import com.irlstream.app.ui.components.TextFieldIRL
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.Destination
import com.irlstream.core.model.PlatformPreset
import com.irlstream.core.rtmp.RtmpUrl
import java.util.Locale

@Composable
fun DestinationsScreen(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    var editing by remember { mutableStateOf<Destination?>(null) }
    var deleting by remember { mutableStateOf<Destination?>(null) }

    AppBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = "Destinations",
                subtitle = "${settings.destinations.count { it.enabled }} enabled · ${settings.destinations.size} configured",
                onBack = { vm.back() },
                actions = {
                    IconAction(Icons.Rounded.Add, "Add destination", { editing = Destination() })
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    text = "One encode, fanned out to every enabled endpoint. If one service drops, the others keep streaming.",
                    icon = Icons.Rounded.Podcasts,
                    color = IRL.Signal,
                )
                SectionHeader("01", "Output rack") {
                    Text(
                        "${settings.destinations.count { it.enabled }}/${settings.destinations.size} ENABLED",
                        style = MonoStyle.copy(fontSize = 8.sp),
                        color = IRL.TextFaint,
                    )
                }
                if (settings.destinations.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Podcasts,
                        title = "Nowhere to stream yet",
                        body = "Add a Twitch, YouTube, Kick, or custom RTMP/RTMPS endpoint. The full URL includes your stream key.",
                        actionLabel = "Add destination",
                        onAction = { editing = Destination() },
                    )
                } else {
                    settings.destinations.forEachIndexed { index, destination ->
                        val runtime = state.destinations.firstOrNull { it.id == destination.id }
                        DestinationCard(
                            index = index,
                            destination = destination,
                            runtime = runtime,
                            onClick = { editing = destination },
                            onToggle = { enabled -> vm.setDestinationEnabled(destination.id, enabled) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton(
                        text = "Add another destination",
                        icon = Icons.Rounded.Add,
                        onClick = { editing = Destination() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val editTarget = editing
    if (editTarget != null) {
        DestinationEditor(
            destination = editTarget,
            isLive = state.isLive,
            onDismiss = { editing = null },
            onSave = { destination ->
                vm.saveDestination(destination)
                editing = null
            },
            onDelete = if (settings.destinations.any { it.id == editTarget.id }) {
                { editing = null; deleting = editTarget }
            } else {
                null
            },
        )
    }

    val deleteTarget = deleting
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = IRL.InkRaised,
            titleContentColor = IRL.Text,
            textContentColor = IRL.TextMuted,
            shape = CutCornerShape(topEnd = 18.dp),
            title = { Text("Remove destination?") },
            text = { Text("${deleteTarget.displayName} will no longer receive your stream.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDestination(deleteTarget.id)
                    deleting = null
                }) {
                    Text("Remove", color = IRL.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel", color = IRL.TextMuted) }
            },
        )
    }
}

@Composable
private fun DestinationCard(
    index: Int,
    destination: Destination,
    runtime: com.irlstream.app.engine.DestinationRuntime?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val status = runtime?.status ?: if (destination.enabled) DestinationStatus.OFFLINE else DestinationStatus.DISABLED
    val live = status == DestinationStatus.LIVE
    val shape = CutCornerShape(topEnd = 14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (destination.enabled) IRL.Panel else IRL.InkRaised)
            .border(1.dp, if (live) IRL.Signal.copy(alpha = 0.35f) else IRL.Line, shape)
            .drawBehind {
                if (live) {
                    drawRect(
                        color = IRL.Signal,
                        size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MonoStyle.copy(fontSize = 9.sp),
                color = IRL.TextFaint,
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(IRL.LineStrong))
            Spacer(Modifier.width(8.dp))
            PlatformMark(destination.preset)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    destination.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (destination.enabled) IRL.Text else IRL.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val host = RtmpUrl.parse(destination.url)?.host
                Text(
                    host ?: "No URL yet",
                    style = MonoStyle.copy(fontSize = 9.sp),
                    color = IRL.TextFaint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(6.dp))
            StatusChip(status, runtime?.bitrateKbps ?: 0)
            Spacer(Modifier.width(8.dp))
            DeckToggle(
                checked = destination.enabled,
                onCheckedChange = onToggle,
            )
        }
        Spacer(Modifier.height(9.dp))
        Hairline()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                RtmpUrl.parse(destination.url)?.masked() ?: "Add the full ingest URL and stream key",
                style = MonoStyle.copy(fontSize = 9.sp),
                color = IRL.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "EDIT",
                style = MicroStyle.copy(fontSize = 8.sp),
                color = IRL.TextMuted,
            )
            Icon(
                Icons.Rounded.Edit,
                null,
                tint = IRL.TextFaint,
                modifier = Modifier.padding(start = 4.dp).size(12.dp),
            )
        }
        if (runtime?.status == DestinationStatus.ERROR && !runtime.message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                runtime.message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = IRL.Danger.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlatformMark(preset: PlatformPreset) {
    val (letter, color) = when (preset) {
        PlatformPreset.TWITCH -> "T" to IRL.Violet
        PlatformPreset.YOUTUBE -> "Y" to IRL.Danger
        PlatformPreset.KICK -> "K" to IRL.Signal
        PlatformPreset.CUSTOM -> "R" to IRL.Blue
    }
    val shape = CutCornerShape(topEnd = 8.dp)
    Box(
        Modifier
            .size(32.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun StatusChip(status: DestinationStatus, bitrateKbps: Int) {
    val (label, color) = when (status) {
        DestinationStatus.LIVE -> (if (bitrateKbps > 0) String.format(Locale.US, "%.1f Mbps", bitrateKbps / 1000f) else "LIVE") to IRL.Signal
        DestinationStatus.CONNECTING -> "CONNECT" to IRL.Warning
        DestinationStatus.RECONNECTING -> "RETRY" to IRL.Warning
        DestinationStatus.ERROR -> "ERROR" to IRL.Danger
        DestinationStatus.DISABLED -> "OFF" to IRL.TextFaint
        DestinationStatus.OFFLINE -> "READY" to IRL.TextMuted
    }
    val shape = CutCornerShape(topEnd = 4.dp)
    Row(
        Modifier
            .height(22.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, size = 5)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MicroStyle.copy(fontSize = 7.sp), color = color)
    }
}

@Composable
private fun DestinationEditor(
    destination: Destination,
    isLive: Boolean,
    onDismiss: () -> Unit,
    onSave: (Destination) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(destination.id) { mutableStateOf(destination.name) }
    var url by remember(destination.id) { mutableStateOf(destination.url) }
    var preset by remember(destination.id) { mutableStateOf(destination.preset) }
    var enabled by remember(destination.id) { mutableStateOf(destination.enabled) }
    var autoReconnect by remember(destination.id) { mutableStateOf(destination.autoReconnect) }
    var reveal by remember { mutableStateOf(false) }

    val valid = RtmpUrl.isValid(url)
    val showError = url.isNotBlank() && !valid

    SheetContent(
        onDismiss = onDismiss,
        title = if (destination.url.isBlank()) "New destination" else "Edit destination",
        subtitle = "Paste the full ingest URL including the stream key.",
    ) {
        MicroLabel("Platform preset", IRL.TextFaint)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlatformPreset.values().forEach { option ->
                ChoiceChip(
                    text = when (option) {
                        PlatformPreset.CUSTOM -> "Custom"
                        PlatformPreset.TWITCH -> "Twitch"
                        PlatformPreset.YOUTUBE -> "YouTube"
                        PlatformPreset.KICK -> "Kick"
                    },
                    selected = preset == option,
                    onClick = {
                        preset = option
                        if (name.isBlank()) {
                            name = when (option) {
                                PlatformPreset.CUSTOM -> "Custom RTMP"
                                PlatformPreset.TWITCH -> "Twitch"
                                PlatformPreset.YOUTUBE -> "YouTube"
                                PlatformPreset.KICK -> "Kick"
                            }
                        }
                        if (url.isBlank()) {
                            url = when (option) {
                                PlatformPreset.TWITCH -> "rtmp://live.twitch.tv/app/"
                                PlatformPreset.YOUTUBE -> "rtmp://a.rtmp.youtube.com/live2/"
                                else -> ""
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TextFieldIRL(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "e.g. Twitch main",
        )
        Spacer(Modifier.height(12.dp))
        TextFieldIRL(
            value = url,
            onValueChange = { url = it.trim() },
            label = "RTMP ingest URL",
            placeholder = "rtmp://live.twitch.tv/app/live_…",
            isError = showError,
            supportingText = when {
                showError -> "Enter a complete rtmp:// or rtmps:// URL with an app and stream key."
                else -> platformHint(preset, url, valid)
            },
            visualTransformation = if (reveal) VisualTransformation.None else StreamKeyMask(),
            trailingIcon = {
                IconAction(
                    icon = if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (reveal) "Hide stream key" else "Show stream key",
                    onClick = { reveal = !reveal },
                    background = Color.Transparent,
                    border = false,
                    size = 38,
                    iconSize = 18,
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                autoCorrectEnabled = false,
            ),
        )
        if (valid) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Key: ${RtmpUrl.parse(url)?.masked()?.substringAfterLast('/')}",
                style = MonoStyle.copy(fontSize = 10.sp),
                color = IRL.TextFaint,
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingSwitchRow(
            title = "Enabled",
            subtitle = "Publish to this destination when you go live",
            checked = enabled,
            onCheckedChange = { enabled = it },
        )
        SettingSwitchRow(
            title = "Auto-reconnect",
            subtitle = "Retry with backoff if the connection drops",
            checked = autoReconnect,
            onCheckedChange = { autoReconnect = it },
        )
        if (isLive) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Saving while live reconnects this output immediately; other outputs keep streaming.",
                style = MaterialTheme.typography.bodySmall,
                color = IRL.Warning,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (destination.url.isBlank()) "Add destination" else "Save changes",
            onClick = {
                onSave(
                    destination.copy(
                        name = name.trim(),
                        url = url.trim(),
                        preset = preset,
                        enabled = enabled,
                        autoReconnect = autoReconnect,
                    ),
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topEnd = 8.dp))
                    .clickable(onClick = onDelete)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, null, tint = IRL.Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove destination", style = MaterialTheme.typography.labelMedium, color = IRL.Danger)
            }
        }
    }
}

@Composable
private fun platformHint(preset: PlatformPreset, url: String, valid: Boolean): String {
    if (valid) return "Stream key is included in the URL."
    return when (preset) {
        PlatformPreset.TWITCH -> "Twitch Dashboard → Settings → Stream → Primary Stream Key. Full URL: rtmp://live.twitch.tv/app/<key>"
        PlatformPreset.YOUTUBE -> "YouTube Studio → Go Live → Stream key. Full URL: rtmp://a.rtmp.youtube.com/live2/<key>"
        PlatformPreset.KICK -> "Copy the RTMPS ingest URL and stream key from your Kick stream settings."
        PlatformPreset.CUSTOM -> "Any rtmp:// or rtmps:// server. The stream key belongs at the end of the URL."
    }
}

/**
 * Masks everything after the app path, preserving the last four characters so the
 * streamer can recognise the key without exposing it on screen.
 */
private class StreamKeyMask : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val raw = text.text
        val scheme = raw.indexOf("://")
        if (scheme < 0) return TransformedText(text, OffsetMapping.Identity)
        val appSlash = raw.indexOf('/', scheme + 3)
        if (appSlash < 0) return TransformedText(text, OffsetMapping.Identity)
        val keySlash = raw.indexOf('/', appSlash + 1)
        if (keySlash < 0) return TransformedText(text, OffsetMapping.Identity)
        val key = raw.substring(keySlash + 1)
        if (key.isBlank()) return TransformedText(text, OffsetMapping.Identity)
        val visible = key.takeLast(4)
        val masked = "•".repeat((key.length - visible.length).coerceAtLeast(0)) + visible
        val transformed = raw.substring(0, keySlash + 1) + masked
        return TransformedText(
            androidx.compose.ui.text.AnnotatedString(transformed),
            OffsetMapping.Identity,
        )
    }
}
