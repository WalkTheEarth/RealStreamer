package com.irlstream.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.R
import com.irlstream.app.engine.EngineState
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.Screen
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.ChoiceChip
import com.irlstream.app.ui.components.IRLSlider
import com.irlstream.app.ui.components.IconAction
import com.irlstream.app.ui.components.InfoBanner
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PanelCard
import com.irlstream.app.ui.components.ScreenTopBar
import com.irlstream.app.ui.components.SectionHeader
import com.irlstream.app.ui.components.SettingActionRow
import com.irlstream.app.ui.components.Segmented
import com.irlstream.app.ui.components.SettingSwitchRow
import com.irlstream.app.ui.components.TextFieldIRL
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.AudioInput
import com.irlstream.core.model.CameraKind
import com.irlstream.core.model.LensFacing
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    AppBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = "Settings",
                subtitle = "Capture, encoding, chat, and console",
                onBack = { vm.back() },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                SectionHeader("01", "Sources & outputs")
                PanelCard(Modifier.fillMaxWidth()) {
                    SettingActionRow(
                        title = "Overlays & sources",
                        subtitle = "On-stream layers and streamer-only panels",
                        value = "${settings.scene.broadcastSources.size} layers",
                        onClick = { vm.open(Screen.Overlays) },
                    )
                    SettingActionRow(
                        title = "Destinations",
                        subtitle = "RTMP / RTMPS outputs",
                        value = "${settings.destinations.size}",
                        onClick = { vm.open(Screen.Destinations) },
                    )
                }
                VideoSection(vm, settings, state)
                AudioSection(vm, settings, state)
                CameraSection(vm, settings, state)
                ChatSection(vm, settings, state)
                ConsoleSection(vm, settings)
                AboutSection()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun VideoSection(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    SectionHeader("02", "Video")
    PanelCard(Modifier.fillMaxWidth()) {
        if (state.isLive && state.pendingVideoSettings) {
            InfoBanner(
                "Video changes are saved and apply on the next stream.",
                icon = Icons.Rounded.Refresh,
            )
            Spacer(Modifier.height(10.dp))
        }
        MicroLabel("Format", IRL.TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            videoPresets.forEach { preset ->
                ChoiceChip(
                    text = preset.label,
                    selected = settings.video.width == preset.width && settings.video.height == preset.height,
                    onClick = {
                        vm.updateVideo {
                            it.copy(width = preset.width, height = preset.height, fps = preset.fps)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        MicroLabel("Frame rate", IRL.TextMuted)
        Spacer(Modifier.height(7.dp))
        Segmented(
            options = listOf("24", "30", "60"),
            selected = settings.video.fps.toString(),
            onSelect = { fps -> vm.updateVideo { it.copy(fps = fps.toInt()) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        IRLSlider(
            label = "Video bitrate",
            value = settings.video.bitrateKbps.toFloat(),
            range = 1000f..12_000f,
            steps = 43,
            valueLabel = String.format(java.util.Locale.US, "%.1f Mbps", settings.video.bitrateKbps / 1000f),
            onValueChange = { value -> vm.updateVideo { it.copy(bitrateKbps = value.roundToInt()) } },
        )
        MicroLabel("Keyframe interval", IRL.TextMuted)
        Spacer(Modifier.height(7.dp))
        Segmented(
            options = listOf("1s", "2s", "4s"),
            selected = "${settings.video.keyframeIntervalSec}s",
            onSelect = { value ->
                vm.updateVideo { it.copy(keyframeIntervalSec = value.removeSuffix("s").toInt()) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Mobile uplink guidance: 720p30 ≈ 3 Mbps · 1080p30 ≈ 5 Mbps. Lower bitrate and 2s keyframes help destinations connect quickly.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextFaint,
        )
    }
}

@Composable
private fun AudioSection(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    SectionHeader("03", "Audio")
    PanelCard(Modifier.fillMaxWidth()) {
        if (state.isLive && state.pendingAudioSettings) {
            InfoBanner("Audio changes are saved and apply on the next stream.")
            Spacer(Modifier.height(10.dp))
        }
        MicroLabel("Input", IRL.TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AudioInput.entries.forEach { input ->
                ChoiceChip(
                    text = input.label,
                    selected = settings.audio.input == input,
                    onClick = { vm.updateAudio { it.copy(input = input) } },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        IRLSlider(
            label = "Audio bitrate",
            value = settings.audio.bitrateKbps.toFloat(),
            range = 64f..320f,
            steps = 7,
            valueLabel = "${settings.audio.bitrateKbps} kbps",
            onValueChange = { value -> vm.updateAudio { it.copy(bitrateKbps = (value / 32f).roundToInt() * 32) } },
        )
        MicroLabel("Channels", IRL.TextMuted)
        Spacer(Modifier.height(7.dp))
        Segmented(
            options = listOf("Mono", "Stereo"),
            selected = if (settings.audio.channels == 1) "Mono" else "Stereo",
            onSelect = { selected -> vm.updateAudio { audio -> audio.copy(channels = if (selected == "Stereo") 2 else 1) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow(
            title = "Echo cancellation",
            subtitle = "Best for speakerphone and outdoor voice",
            checked = settings.audio.echoCancellation,
            onCheckedChange = { vm.updateAudio { audio -> audio.copy(echoCancellation = it) } },
        )
        SettingSwitchRow(
            title = "Noise suppression",
            checked = settings.audio.noiseSuppression,
            onCheckedChange = { vm.updateAudio { audio -> audio.copy(noiseSuppression = it) } },
        )
        SettingSwitchRow(
            title = "Automatic gain",
            checked = settings.audio.autoGain,
            onCheckedChange = { vm.updateAudio { audio -> audio.copy(autoGain = it) } },
        )
        SettingSwitchRow(
            title = "Mute microphone",
            subtitle = "Video keeps streaming; audio packets pause",
            checked = settings.audio.muted,
            onCheckedChange = { vm.updateAudio { audio -> audio.copy(muted = it) } },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Audio is captured from the phone microphone, including while a UVC camera is connected.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextFaint,
        )
    }
}

@Composable
private fun CameraSection(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    SectionHeader("04", "Camera")
    PanelCard(Modifier.fillMaxWidth()) {
        MicroLabel("Built-in cameras", IRL.TextMuted)
        Spacer(Modifier.height(8.dp))
        if (state.builtInCameras.isEmpty()) {
            InfoBanner(
                "No built-in cameras reported yet. Check the camera permission.",
                icon = Icons.Rounded.Warning,
                color = IRL.Warning,
            )
        } else {
            state.builtInCameras.forEach { camera ->
                CameraSettingRow(
                    title = camera.label,
                    subtitle = "Camera ${camera.id} · up to ${camera.maxWidth}×${camera.maxHeight}",
                    icon = if (camera.facing == LensFacing.FRONT) Icons.Rounded.Cameraswitch else Icons.Rounded.Videocam,
                    selected = settings.camera.kind == CameraKind.BUILT_IN && (
                        settings.camera.cameraId == camera.id ||
                            (settings.camera.cameraId == null && settings.camera.lens == camera.facing)
                        ),
                    onClick = {
                        vm.selectCamera(CameraKind.BUILT_IN, camera.facing, null, camera.id)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("UVC / USB webcam", IRL.TextMuted, Modifier.weight(1f))
            Text(
                "REFRESH",
                style = com.irlstream.app.ui.theme.MicroStyle.copy(fontSize = 8.sp),
                color = IRL.Signal,
                modifier = Modifier
                    .clickable { vm.refreshUvc() }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.uvcDevices.isEmpty()) {
            InfoBanner(
                "No UVC camera detected. Connect a webcam or put your action camera in webcam mode.",
                icon = Icons.Rounded.Usb,
                color = IRL.TextMuted,
            )
        } else {
            state.uvcDevices.forEach { device ->
                CameraSettingRow(
                    title = device.name,
                    subtitle = "USB %04X:%04X · generic UVC".format(device.vendorId, device.productId),
                    icon = Icons.Rounded.Usb,
                    selected = settings.camera.kind == CameraKind.USB_UVC && settings.camera.uvcDeviceKey == device.key,
                    onClick = { vm.selectCamera(CameraKind.USB_UVC, settings.camera.lens, device.key) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow(
            title = "Fall back to built-in camera",
            subtitle = "Keep streaming if a UVC camera is unplugged",
            checked = settings.camera.fallbackToBuiltIn,
            onCheckedChange = { enabled -> vm.updateCamera { it.copy(fallbackToBuiltIn = enabled) } },
        )
        SettingSwitchRow(
            title = "Mirror front camera",
            checked = settings.camera.mirrorFrontCamera,
            onCheckedChange = { enabled -> vm.updateCamera { it.copy(mirrorFrontCamera = enabled) } },
        )
        Text(
            "UVC support is generic USB Video Class. No camera-specific drivers or hacks.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextFaint,
        )
    }
}

@Composable
private fun ChatSection(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    var revealToken by remember { mutableStateOf(false) }
    SectionHeader("05", "Chat")
    PanelCard(Modifier.fillMaxWidth()) {
        Text(
            state.chatStatus,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        MicroLabel("Twitch", IRL.Violet)
        Spacer(Modifier.height(7.dp))
        TextFieldIRL(
            value = settings.chat.twitchChannel,
            onValueChange = { value -> vm.updateChat { it.copy(twitchChannel = value.trim()) } },
            label = "Channel name",
            placeholder = "your_channel",
        )
        Spacer(Modifier.height(8.dp))
        TextFieldIRL(
            value = settings.chat.twitchToken,
            onValueChange = { value -> vm.updateChat { it.copy(twitchToken = value.trim()) } },
            label = "OAuth token (optional)",
            placeholder = "Without the oauth: prefix · optional",
            visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconAction(
                    icon = if (revealToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Reveal token",
                    onClick = { revealToken = !revealToken },
                    background = Color.Transparent,
                    border = false,
                    size = 38,
                    iconSize = 17,
                )
            },
        )
        TextFieldIRL(
            value = settings.chat.twitchUsername,
            onValueChange = { value -> vm.updateChat { it.copy(twitchUsername = value.trim()) } },
            label = "Twitch username (only with a token)",
            placeholder = "your_account",
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        MicroLabel("Custom WebSocket", IRL.Blue)
        Spacer(Modifier.height(7.dp))
        TextFieldIRL(
            value = settings.chat.customWebSocketUrl,
            onValueChange = { value -> vm.updateChat { it.copy(customWebSocketUrl = value.trim()) } },
            label = "wss:// URL",
            placeholder = "wss://your-relay.example/chat",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFieldIRL(
                value = settings.chat.customUserField,
                onValueChange = { value -> vm.updateChat { it.copy(customUserField = value.trim()) } },
                label = "User field",
                modifier = Modifier.weight(1f),
            )
            TextFieldIRL(
                value = settings.chat.customMessageField,
                onValueChange = { value -> vm.updateChat { it.copy(customMessageField = value.trim()) } },
                label = "Message field",
                modifier = Modifier.weight(1f),
            )
        }
        TextFieldIRL(
            value = settings.chat.customColorField,
            onValueChange = { value -> vm.updateChat { it.copy(customColorField = value.trim()) } },
            label = "Color field (optional)",
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow(
            title = "Demo messages",
            subtitle = "Local sample chat for positioning overlays",
            checked = settings.chat.demoMessages,
            onCheckedChange = { enabled -> vm.updateChat { it.copy(demoMessages = enabled) } },
        )
        SettingSwitchRow(
            title = "Hide command messages in overlays",
            subtitle = "Messages starting with ! stay in the private console",
            checked = settings.chat.hideCommands,
            onCheckedChange = { enabled -> vm.updateChat { it.copy(hideCommands = enabled) } },
        )
        Text(
            "Twitch chat connects read-only. Custom sources expect JSON with configurable field paths, e.g. data.user.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextFaint,
        )
    }
}

@Composable
private fun ConsoleSection(vm: AppViewModel, settings: AppSettings) {
    SectionHeader("06", "Streamer console")
    PanelCard(Modifier.fillMaxWidth()) {
        SettingSwitchRow(
            title = "Show the console",
            subtitle = "A local-only sidebar with chat, stats, outputs and audio",
            checked = settings.console.visible,
            onCheckedChange = { visible -> vm.updateConsole { it.copy(visible = visible) } },
        )
        IRLSlider(
            label = "Console width",
            value = settings.console.widthDp.toFloat(),
            range = 280f..420f,
            steps = 6,
            valueLabel = "${settings.console.widthDp} dp",
            onValueChange = { value -> vm.updateConsole { it.copy(widthDp = (value / 20f).roundToInt() * 20) } },
        )
        Text(
            "The console is drawn by the app, not the compositor. Nothing in this column is encoded into the stream.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = IRL.TextFaint,
        )
    }
}

@Composable
private fun AboutSection() {
    SectionHeader("07", "About")
    PanelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val logoShape = CutCornerShape(topEnd = 8.dp)
            Image(
                painter = painterResource(R.drawable.realstreamer_badge),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(logoShape)
                    .border(1.dp, IRL.LineStrong, logoShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "REALSTREAMER",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.6.sp),
                    color = IRL.Text,
                )
                MicroLabel("Broadcast deck", IRL.TextFaint)
            }
            Text("1.0.1", style = MonoStyle.copy(fontSize = 10.sp), color = IRL.TextMuted)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Android-first IRL broadcasting. One GPU-composited scene, hardware H.264/AAC encoding, and independent RTMP/RTMPS outputs.",
            style = MaterialTheme.typography.bodySmall,
            color = IRL.TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "UVC camera stack: AndroidUSBCamera (Apache-2.0). Networking: OkHttp. UI: Jetpack Compose. RTMP publishing is implemented in-house with TLS support.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = IRL.TextFaint,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(IRL.Signal))
            Spacer(Modifier.width(7.dp))
            Text("WYSIWYG compositor · UVC · multi-output RTMP", style = MonoStyle.copy(fontSize = 9.sp), color = IRL.TextFaint)
        }
    }
}

@Composable
private fun CameraSettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(topEnd = 8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) IRL.SignalFaint else IRL.PanelHigh)
            .border(1.dp, if (selected) IRL.Signal.copy(alpha = 0.4f) else IRL.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) IRL.Signal else IRL.TextMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MicroStyle.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = IRL.TextMuted, maxLines = 1)
        }
        if (selected) {
            val checkShape = CutCornerShape(topEnd = 4.dp)
            Box(
                Modifier
                    .size(17.dp)
                    .clip(checkShape)
                    .background(IRL.Signal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = IRL.Ink, modifier = Modifier.size(12.dp))
            }
        }
    }
}

private data class VideoPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
)

private val videoPresets = listOf(
    VideoPreset("720p30", 1280, 720, 30),
    VideoPreset("1080p30", 1920, 1080, 30),
    VideoPreset("1080p60", 1920, 1080, 60),
    VideoPreset("9:16", 720, 1280, 30),
)

private val AudioInput.label: String
    get() = when (this) {
        AudioInput.VOICE_COMMUNICATION -> "Voice / AEC"
        AudioInput.CAMCORDER -> "Camcorder"
        AudioInput.MIC -> "Mic"
        AudioInput.VOICE_RECOGNITION -> "Raw"
    }
