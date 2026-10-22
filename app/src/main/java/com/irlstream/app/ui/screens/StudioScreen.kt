package com.irlstream.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.R
import com.irlstream.app.engine.BuiltInCameraInfo
import com.irlstream.app.engine.ChatMessage
import com.irlstream.app.engine.ChatPlatform
import com.irlstream.app.engine.DestinationRuntime
import com.irlstream.app.engine.DestinationStatus
import com.irlstream.app.engine.EngineState
import com.irlstream.app.engine.StreamPhase
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.Screen
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.ChoiceChip
import com.irlstream.app.ui.components.ControlButton
import com.irlstream.app.ui.components.DeckPlate
import com.irlstream.app.ui.components.EnginePreview
import com.irlstream.app.ui.components.EngineSurface
import com.irlstream.app.ui.components.EmptyState
import com.irlstream.app.ui.components.Hairline
import com.irlstream.app.ui.components.IconAction
import com.irlstream.app.ui.components.InfoBanner
import com.irlstream.app.ui.components.LevelMeter
import com.irlstream.app.ui.components.LiveBadge
import com.irlstream.app.ui.components.LockStreamOrientation
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PanelCard
import com.irlstream.app.ui.components.PrimaryButton
import com.irlstream.app.ui.components.SecondaryButton
import com.irlstream.app.ui.components.SectionHeader
import com.irlstream.app.ui.components.Segmented
import com.irlstream.app.ui.components.SheetContent
import com.irlstream.app.ui.components.Sparkline
import com.irlstream.app.ui.components.StatusDot
import com.irlstream.app.ui.components.SettingSwitchRow
import com.irlstream.app.ui.components.formatBitrate
import com.irlstream.app.ui.components.findActivity
import com.irlstream.app.ui.components.formatDuration
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.CameraKind
import com.irlstream.core.model.ConsolePanel
import com.irlstream.core.model.LensFacing
import com.irlstream.core.model.SourceConfig
import com.irlstream.core.model.SourceType

@Composable
fun StudioScreen(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    cameraGranted: Boolean,
    micGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val activeWidth = if (state.outputWidth > 0) state.outputWidth else settings.video.width
    val activeHeight = if (state.outputHeight > 0) state.outputHeight else settings.video.height
    val activeVideo = settings.video.copy(width = activeWidth, height = activeHeight)
    LockStreamOrientation(activeVideo)
    val live = state.isLive
    val busy = state.isBusy
    val aspect = activeWidth.toFloat() / activeHeight
    var cameraSheet by remember { mutableStateOf(false) }
    val bitrateHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(state.isLive, state.durationMs) {
        if (state.isLive) {
            bitrateHistory.add(state.videoBitrateKbps.toFloat())
            if (bitrateHistory.size > 48) bitrateHistory.removeAt(0)
        } else if (bitrateHistory.isNotEmpty()) {
            bitrateHistory.clear()
        }
    }

    val goLive = {
        when {
            live -> vm.stopLive()
            busy -> Unit
            !cameraGranted || !micGranted -> onRequestPermissions()
            settings.activeDestinations.isEmpty() -> {
                vm.showToast("Add a destination first")
                vm.open(Screen.Destinations)
            }
            else -> vm.goLive()
        }
    }

    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp && maxWidth > maxHeight
        val compact = maxWidth < 620.dp
        val consoleWidth = maxOf(240.dp, minOf(settings.console.widthDp.dp, maxWidth * 0.40f))
        val resolutionLabel = state.resolution.ifBlank { activeVideo.display }
        val resolutionShort = "${activeWidth}×${activeHeight}"
        if (wide) {
            Row(Modifier.fillMaxSize().background(IRL.Ink)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(IRL.InkRaised)
                            .statusBarsPadding(),
                    ) {
                        StudioTopBar(
                            vm = vm,
                            state = state,
                            compact = false,
                        )
                    }
                    ProgramRail(
                        state = state,
                        resolutionLabel = resolutionLabel,
                        resolutionShort = resolutionShort,
                        cameraLabel = state.cameraLabel,
                        onCameraClick = { cameraSheet = true },
                        compact = false,
                    )
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                    ) {
                        val containerAspect = maxWidth.value / maxHeight.value
                        val stageW = if (containerAspect > aspect) maxHeight * aspect else maxWidth
                        val stageH = stageW / aspect
                        if (cameraGranted) {
                            Box(Modifier.size(stageW, stageH).align(Alignment.Center)) {
                                EngineSurface(Modifier.fillMaxSize())
                            }
                        } else {
                            Box(Modifier.size(stageW, stageH).align(Alignment.Center)) {
                                PermissionCard(aspect = aspect, onRequestPermissions = onRequestPermissions)
                            }
                        }
                    }
                    WideTransportStrip(
                        state = state,
                        micGranted = micGranted,
                        onRequestPermissions = onRequestPermissions,
                        onGoLive = goLive,
                        onOpenDestinations = { vm.open(Screen.Destinations) },
                    )
                }
                Box(
                    Modifier
                        .width(consoleWidth)
                        .fillMaxHeight()
                        .background(IRL.InkRaised),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(IRL.Line)
                            .align(Alignment.TopStart),
                    )
                    if (settings.console.visible) {
                        ConsoleColumn(
                            vm = vm,
                            settings = settings,
                            state = state,
                            history = bitrateHistory,
                            micGranted = micGranted,
                            onRequestPermissions = onRequestPermissions,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        )
                    } else {
                        CollapsedConsoleRail(
                            modifier = Modifier.fillMaxHeight().padding(6.dp),
                            onShow = { vm.updateConsole { it.copy(visible = true) } },
                        )
                    }
                }
            }
        } else {
            AppBackground {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    StudioTopBar(
                        vm = vm,
                        state = state,
                        compact = compact,
                    )
                    ProgramRail(
                        state = state,
                        resolutionLabel = resolutionLabel,
                        resolutionShort = resolutionShort,
                        cameraLabel = state.cameraLabel,
                        onCameraClick = { cameraSheet = true },
                        compact = true,
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                    ) {
                        PreviewPane(
                            vm = vm,
                            settings = settings,
                            state = state,
                            cameraGranted = cameraGranted,
                            micGranted = micGranted,
                            aspect = aspect,
                            onRequestPermissions = onRequestPermissions,
                            onGoLive = goLive,
                        )
                        Spacer(Modifier.height(14.dp))
                        if (settings.console.visible) {
                            ConsoleContent(
                                vm = vm,
                                settings = settings,
                                state = state,
                                history = bitrateHistory,
                            )
                        } else {
                            SecondaryButton(
                                text = "Show streamer console",
                                icon = Icons.Rounded.Visibility,
                                onClick = { vm.updateConsole { it.copy(visible = true) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (cameraSheet) {
        CameraPickerSheet(
            vm = vm,
            settings = settings,
            state = state,
            onDismiss = { cameraSheet = false },
            onSelectBuiltIn = { camera ->
                vm.selectCamera(CameraKind.BUILT_IN, camera.facing, null, camera.id)
                cameraSheet = false
            },
            onSelectUvc = { key ->
                vm.selectCamera(CameraKind.USB_UVC, settings.camera.lens, key)
                cameraSheet = false
            },
            onRefresh = { vm.refreshUvc() },
        )
    }
}

@Composable
private fun StudioTopBar(
    vm: AppViewModel,
    state: EngineState,
    compact: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val logoShape = CutCornerShape(topEnd = 9.dp)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(logoShape)
                    .background(IRL.Ink)
                    .border(1.dp, IRL.LineStrong, logoShape),
            ) {
                Image(
                    painter = painterResource(R.drawable.realstreamer_badge),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "REALSTREAMER",
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.7.sp),
                        color = IRL.Text,
                    )
                    MicroLabel("Broadcast deck", IRL.TextFaint)
                }
            }
            Spacer(Modifier.weight(1f))
            LiveBadge(
                live = state.isLive,
                durationMs = state.durationMs,
                idleLabel = if (state.phase == StreamPhase.PREVIEW) "PREVIEW" else "READY",
            )
            Spacer(Modifier.width(10.dp))
            IconAction(
                Icons.Rounded.Layers,
                "Overlays",
                { vm.open(Screen.Overlays) },
                size = 36,
                iconSize = 17,
            )
            Spacer(Modifier.width(5.dp))
            IconAction(
                Icons.Rounded.Podcasts,
                "Destinations",
                { vm.open(Screen.Destinations) },
                size = 36,
                iconSize = 17,
            )
            Spacer(Modifier.width(5.dp))
            IconAction(
                Icons.Rounded.Settings,
                "Settings",
                { vm.open(Screen.Settings) },
                size = 36,
                iconSize = 17,
            )
        }
        Hairline()
    }
}

@Composable
private fun ProgramRail(
    state: EngineState,
    resolutionLabel: String,
    resolutionShort: String,
    cameraLabel: String,
    onCameraClick: () -> Unit,
    compact: Boolean,
) {
    Column(Modifier.fillMaxWidth().background(IRL.Ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (state.isLive) IRL.Live else IRL.Signal),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "PROGRAM",
                style = MicroStyle.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
                color = IRL.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            CameraChip(
                label = cameraLabel,
                onClick = onCameraClick,
                maxWidth = if (compact) 128.dp else 170.dp,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                if (compact) resolutionShort else resolutionLabel,
                style = MonoStyle.copy(fontSize = 9.sp),
                color = IRL.TextFaint,
                maxLines = 1,
            )
        }
        Hairline()
    }
}

@Composable
private fun PreviewPane(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    cameraGranted: Boolean,
    micGranted: Boolean,
    aspect: Float,
    onRequestPermissions: () -> Unit,
    onGoLive: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (cameraGranted) {
            val previewMaxHeight = 360.dp
            BoxWithConstraints(
                Modifier.fillMaxWidth().heightIn(max = previewMaxHeight),
                contentAlignment = Alignment.Center,
            ) {
                val frameWidth = minOf(maxWidth, previewMaxHeight * aspect)
                val frameHeight = frameWidth / aspect
                Box(
                    Modifier
                        .size(frameWidth, frameHeight)
                        .background(Color.Black)
                        .border(1.dp, IRL.LineStrong),
                ) {
                    EnginePreview(aspectRatio = aspect, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            PermissionCard(aspect = aspect, onRequestPermissions = onRequestPermissions)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckPlate("WYSIWYG", color = IRL.Signal)
            Spacer(Modifier.width(8.dp))
            Text(
                "Viewers see this exact frame",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = IRL.TextFaint,
            )
            Spacer(Modifier.weight(1f))
            if (!micGranted) {
                MicWarning(onRequestPermissions)
            }
        }
        Spacer(Modifier.height(12.dp))
        DestinationPills(
            state = state,
            onOpenDestinations = { vm.open(Screen.Destinations) },
        )
        Spacer(Modifier.height(10.dp))
        ControlBar(
            vm = vm,
            settings = settings,
            state = state,
            micGranted = micGranted,
            onRequestPermissions = onRequestPermissions,
            onGoLive = onGoLive,
        )
    }
}

@Composable
private fun PermissionCard(aspect: Float, onRequestPermissions: () -> Unit) {
    val shape = CutCornerShape(topEnd = 18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape)
            .background(IRL.Panel)
            .border(1.dp, IRL.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MicroLabel("INPUT / CAMERA", IRL.TextFaint)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CutCornerShape(topEnd = 13.dp))
                    .background(IRL.SignalFaint)
                    .border(1.dp, IRL.Signal.copy(alpha = 0.3f), CutCornerShape(topEnd = 13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Videocam, null, tint = IRL.Signal, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Camera access is off", style = MaterialTheme.typography.titleLarge, color = IRL.Text)
            Spacer(Modifier.height(5.dp))
            Text(
                "RealStreamer needs camera and microphone access for the live preview and your broadcast. If no dialog appears, enable both in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = IRL.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Allow camera & mic", onRequestPermissions)
        }
    }
}

@Composable
private fun CameraChip(label: String, onClick: () -> Unit, maxWidth: androidx.compose.ui.unit.Dp = 220.dp) {
    val shape = CutCornerShape(topEnd = 5.dp)
    Row(
        Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(IRL.Panel)
            .border(1.dp, IRL.LineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Videocam, null, tint = IRL.TextMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label.ifBlank { "Choose camera" },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = IRL.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = IRL.TextFaint, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun WideTransportStrip(
    state: EngineState,
    micGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onGoLive: () -> Unit,
    onOpenDestinations: () -> Unit,
) {
    val live = state.isLive
    val busy = state.isBusy
    Column(
        Modifier
            .fillMaxWidth()
            .background(IRL.InkRaised)
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DestinationPills(
                state = state,
                onOpenDestinations = onOpenDestinations,
                modifier = Modifier.weight(1f),
            )
            if (!micGranted) {
                Spacer(Modifier.width(8.dp))
                MicWarning(onRequestPermissions)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(28.dp).background(IRL.Line))
            Spacer(Modifier.width(10.dp))
            PrimaryButton(
                text = when {
                    busy -> "WAIT"
                    live -> "END"
                    else -> "GO LIVE"
                },
                icon = if (live) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                danger = live,
                enabled = !busy,
                onClick = onGoLive,
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

@Composable
private fun DestinationPills(
    state: EngineState,
    onOpenDestinations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.destinations.isEmpty()) {
            val shape = CutCornerShape(topEnd = 5.dp)
            Row(
                Modifier
                    .height(28.dp)
                    .clip(shape)
                    .background(IRL.Panel)
                    .border(1.dp, IRL.Line, shape)
                    .clickable(onClick = onOpenDestinations)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("--", style = MonoStyle.copy(fontSize = 8.sp), color = IRL.TextFaint)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(1.dp).height(10.dp).background(IRL.LineStrong))
                Spacer(Modifier.width(6.dp))
                StatusDot(DestinationStatus.ERROR, size = 6)
                Spacer(Modifier.width(6.dp))
                Text(
                    "NO OUTPUTS · ADD ONE",
                    style = MicroStyle.copy(fontSize = 8.sp),
                    color = IRL.TextMuted,
                )
            }
        } else {
            state.destinations.forEachIndexed { index, destination ->
                DestinationPill(destination, index, onOpenDestinations)
            }
        }
    }
}

@Composable
private fun DestinationPill(destination: DestinationRuntime, index: Int, onClick: () -> Unit) {
    val shape = CutCornerShape(topEnd = 5.dp)
    val live = destination.status == DestinationStatus.LIVE
    Row(
        Modifier
            .height(28.dp)
            .clip(shape)
            .background(IRL.Panel)
            .border(1.dp, if (live) IRL.Signal.copy(alpha = 0.28f) else IRL.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            style = MonoStyle.copy(fontSize = 8.sp),
            color = IRL.TextFaint,
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(1.dp).height(10.dp).background(IRL.LineStrong))
        Spacer(Modifier.width(6.dp))
        StatusDot(destination.status, size = 6)
        Spacer(Modifier.width(6.dp))
        Text(
            destination.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = if (destination.status == DestinationStatus.DISABLED) IRL.TextFaint else IRL.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (live && destination.bitrateKbps > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                formatBitrate(destination.bitrateKbps),
                style = MonoStyle.copy(fontSize = 8.sp),
                color = IRL.Signal,
            )
        }
    }
}

@Composable
private fun ControlBar(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    micGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onGoLive: () -> Unit,
) {
    val live = state.isLive
    val busy = state.isBusy
    val activeCount = state.destinations.count { it.status != DestinationStatus.DISABLED }
    val liveCount = state.destinations.count { it.status == DestinationStatus.LIVE }
    val shape = CutCornerShape(topEnd = 14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IRL.Panel)
            .border(1.dp, IRL.Line, shape)
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = Icons.Rounded.Cameraswitch,
                label = "FLIP",
                onClick = { vm.switchCamera() },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = if (settings.audio.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (settings.audio.muted) "MUTED" else "MIC",
                onClick = {
                    if (!micGranted) onRequestPermissions() else vm.setMuted(!settings.audio.muted)
                },
                active = !settings.audio.muted && micGranted,
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Layers,
                label = "LAYERS",
                onClick = { vm.open(Screen.Overlays) },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Podcasts,
                label = "OUTPUTS",
                onClick = { vm.open(Screen.Destinations) },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Settings,
                label = "SETTINGS",
                onClick = { vm.open(Screen.Settings) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(5.dp))
        Hairline()
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroLabel(
                when {
                    live -> "LIVE · $liveCount OF $activeCount OUTPUTS"
                    activeCount == 0 -> "NO OUTPUTS CONFIGURED"
                    else -> "$activeCount OUTPUTS READY"
                },
                color = when {
                    live -> IRL.Signal
                    activeCount == 0 -> IRL.Warning
                    else -> IRL.TextMuted
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                settings.video.display,
                style = MonoStyle.copy(fontSize = 9.sp),
                color = IRL.TextFaint,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(7.dp))
        PrimaryButton(
            text = when {
                busy -> "STOPPING…"
                live -> "END STREAM"
                else -> "GO LIVE"
            },
            icon = if (live) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            danger = live,
            enabled = !busy,
            onClick = onGoLive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MicWarning(onRequestPermissions: () -> Unit) {
    val shape = CutCornerShape(topEnd = 5.dp)
    Row(
        Modifier
            .height(24.dp)
            .clip(shape)
            .background(IRL.DangerFaint)
            .border(1.dp, IRL.Danger.copy(alpha = 0.35f), shape)
            .clickable(onClick = onRequestPermissions)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MicOff, null, tint = IRL.Danger, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text("MIC OFF", style = MicroStyle.copy(fontSize = 8.sp), color = IRL.Danger)
    }
}

@Composable
private fun ConsoleColumn(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    history: List<Float>,
    micGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ConsoleHeader(
            vm = vm,
            state = state,
            onHide = { vm.updateConsole { it.copy(visible = false) } },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            ConsoleContent(vm, settings, state, history)
            Spacer(Modifier.height(16.dp))
        }
        ConsoleControls(
            vm = vm,
            settings = settings,
            micGranted = micGranted,
            onRequestPermissions = onRequestPermissions,
        )
    }
}

@Composable
private fun ConsoleControls(
    vm: AppViewModel,
    settings: AppSettings,
    micGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = Icons.Rounded.Cameraswitch,
                label = "FLIP",
                onClick = { vm.switchCamera() },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = if (settings.audio.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (settings.audio.muted) "MUTED" else "MIC",
                onClick = {
                    if (!micGranted) onRequestPermissions() else vm.setMuted(!settings.audio.muted)
                },
                active = !settings.audio.muted && micGranted,
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Layers,
                label = "LAYERS",
                onClick = { vm.open(Screen.Overlays) },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Podcasts,
                label = "OUTPUTS",
                onClick = { vm.open(Screen.Destinations) },
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = Icons.Rounded.Settings,
                label = "CONFIG",
                onClick = { vm.open(Screen.Settings) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConsoleHeader(vm: AppViewModel, state: EngineState, onHide: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("Operator / Local", IRL.TextFaint, Modifier.weight(1f))
                DeckPlate("LOCAL ONLY", color = IRL.Warning)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CONSOLE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = IRL.Text,
                    modifier = Modifier.weight(1f),
                )
                IconAction(
                    Icons.Rounded.VisibilityOff,
                    "Hide console",
                    onHide,
                    background = Color.Transparent,
                    border = false,
                    size = 30,
                    iconSize = 16,
                    tint = IRL.TextMuted,
                )
                IconAction(
                    Icons.Rounded.Layers,
                    "Edit streamer panels",
                    { vm.open(Screen.Overlays) },
                    background = Color.Transparent,
                    border = false,
                    size = 30,
                    iconSize = 16,
                    tint = IRL.TextMuted,
                )
            }
            Text(
                "Chat and telemetry here are never broadcast.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = IRL.TextFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Hairline()
    }
}

@Composable
private fun ConsoleContent(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    history: List<Float>,
) {
    val panels = settings.scene.streamerSources.filter { it.enabled }.sortedBy { it.order }
    Column(Modifier.fillMaxWidth()) {
        if (panels.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Chat,
                title = "Your console is empty",
                body = "Add streamer-only chat, stats, output status, or an audio meter.",
                actionLabel = "Add streamer panels",
                onAction = { vm.open(Screen.Overlays) },
            )
        }
        panels.forEach { source ->
            val panel = (source.config as? SourceConfig.Console)?.panel ?: return@forEach
            when (panel) {
                ConsolePanel.CHAT -> ChatConsolePanel(state)
                ConsolePanel.STATS -> StatsConsolePanel(state, history)
                ConsolePanel.OUTPUTS -> OutputsConsolePanel(state) { vm.open(Screen.Destinations) }
                ConsolePanel.AUDIO -> AudioConsolePanel(
                    state = state,
                    settings = settings,
                    onToggleMute = { vm.setMuted(!settings.audio.muted) },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DeckPanelTitle(
    index: String,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = MonoStyle.copy(fontSize = 9.sp), color = IRL.Signal)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(1.dp).height(10.dp).background(IRL.LineStrong))
        Spacer(Modifier.width(7.dp))
        Text(
            title.uppercase(java.util.Locale.US),
            style = MaterialTheme.typography.labelLarge,
            color = IRL.Text,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    Spacer(Modifier.height(7.dp))
    Hairline()
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun ChatConsolePanel(state: EngineState) {
    PanelCard(Modifier.fillMaxWidth().animateContentSize()) {
        DeckPanelTitle("01", "Chat") {
            Text(
                state.chatStatus,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = IRL.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        val messages = state.chat.takeLast(9)
        if (messages.isEmpty()) {
            Text(
                "No messages yet. Configure chat in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = IRL.TextFaint,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                messages.forEach { message -> ChatConsoleLine(message) }
            }
        }
    }
}

@Composable
private fun ChatConsoleLine(message: ChatMessage) {
    val nameColor = message.colorArgb.let { argb ->
        val c = Color(argb.toInt())
        if (c.alpha == 0f) IRL.Signal else c
    }
    val platformColor = when (message.platform) {
        ChatPlatform.TWITCH -> IRL.Violet
        ChatPlatform.CUSTOM -> IRL.Blue
        ChatPlatform.DEMO -> IRL.TextFaint
    }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold)) {
            append(message.author)
        }
        withStyle(SpanStyle(color = IRL.TextFaint)) { append("  ") }
        withStyle(SpanStyle(color = IRL.TextSecondary)) { append(message.text) }
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(2.dp)
                .height(12.dp)
                .background(platformColor),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsConsolePanel(state: EngineState, history: List<Float>) {
    val liveOutputs = state.destinations.count { it.status == DestinationStatus.LIVE }
    val activeOutputs = state.destinations.count { it.status != DestinationStatus.DISABLED }
    PanelCard(Modifier.fillMaxWidth()) {
        DeckPanelTitle("02", "Telemetry") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    if (state.isLive) DestinationStatus.LIVE else DestinationStatus.OFFLINE,
                    size = 6,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (state.isLive) "LIVE" else "IDLE",
                    style = MicroStyle.copy(fontSize = 8.sp),
                    color = if (state.isLive) IRL.Signal else IRL.TextFaint,
                )
            }
        }
        StatRow(
            leftLabel = "FPS",
            leftValue = String.format(java.util.Locale.US, "%.1f", state.fps),
            rightLabel = "CAM FPS",
            rightValue = String.format(java.util.Locale.US, "%.1f", state.cameraFps),
        )
        Hairline()
        StatRow(
            leftLabel = "BITRATE",
            leftValue = formatBitrate(state.videoBitrateKbps),
            rightLabel = "DROPPED",
            rightValue = state.droppedFrames.toString(),
        )
        Hairline()
        StatRow(
            leftLabel = "UPTIME",
            leftValue = formatDuration(state.durationMs),
            rightLabel = "OUTPUTS",
            rightValue = "$liveOutputs/$activeOutputs",
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Bitrate history", IRL.TextFaint)
            Spacer(Modifier.weight(1f))
            Text(
                formatBitrate(state.videoBitrateKbps),
                style = MonoStyle.copy(fontSize = 9.sp),
                color = IRL.Signal,
            )
        }
        Spacer(Modifier.height(4.dp))
        Sparkline(history, Modifier.fillMaxWidth().height(34.dp))
    }
}

@Composable
private fun StatRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(Modifier.fillMaxWidth()) {
        StatCell(leftLabel, leftValue, Modifier.weight(1f), divider = true)
        StatCell(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
) {
    Column(
        modifier
            .drawBehind {
                if (divider) {
                    drawLine(
                        IRL.Line,
                        Offset(size.width - 1f, 0f),
                        Offset(size.width - 1f, size.height),
                        1f,
                    )
                }
            }
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        MicroLabel(label, IRL.TextFaint)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MonoStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = IRL.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OutputsConsolePanel(state: EngineState, onOpenDestinations: () -> Unit) {
    PanelCard(Modifier.fillMaxWidth()) {
        DeckPanelTitle("03", "Outputs") {
            Text(
                "EDIT",
                style = MicroStyle.copy(fontSize = 8.sp),
                color = IRL.Signal,
                modifier = Modifier
                    .clickable(onClick = onOpenDestinations)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            )
        }
        if (state.destinations.isEmpty()) {
            Text(
                "No destinations configured.",
                style = MaterialTheme.typography.bodySmall,
                color = IRL.TextFaint,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            state.destinations.forEachIndexed { index, destination ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            style = MonoStyle.copy(fontSize = 8.sp),
                            color = IRL.TextFaint,
                        )
                        Spacer(Modifier.width(7.dp))
                        StatusDot(destination.status, size = 6)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                destination.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (destination.status == DestinationStatus.DISABLED) IRL.TextFaint else IRL.Text,
                                maxLines = 1,
                            )
                            Text(
                                destination.maskedUrl,
                                style = MonoStyle.copy(fontSize = 8.sp),
                                color = IRL.TextFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (destination.status) {
                                DestinationStatus.LIVE -> formatBitrate(destination.bitrateKbps)
                                DestinationStatus.CONNECTING -> "CONNECT"
                                DestinationStatus.RECONNECTING -> "RETRY ${destination.reconnectAttempts}"
                                DestinationStatus.ERROR -> "ERROR"
                                DestinationStatus.DISABLED -> "OFF"
                                DestinationStatus.OFFLINE -> "IDLE"
                            },
                            style = MicroStyle.copy(fontSize = 8.sp),
                            color = when (destination.status) {
                                DestinationStatus.LIVE -> IRL.Signal
                                DestinationStatus.ERROR -> IRL.Danger
                                DestinationStatus.RECONNECTING, DestinationStatus.CONNECTING -> IRL.Warning
                                else -> IRL.TextFaint
                            },
                        )
                    }
                    if (destination.status == DestinationStatus.ERROR && !destination.message.isNullOrBlank()) {
                        Text(
                            destination.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = IRL.Danger.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index < state.destinations.lastIndex) Hairline()
            }
        }
    }
}

@Composable
private fun AudioConsolePanel(
    state: EngineState,
    settings: AppSettings,
    onToggleMute: () -> Unit,
) {
    PanelCard(Modifier.fillMaxWidth()) {
        DeckPanelTitle("04", "Audio") {
            Text(
                settings.audio.input.name.replace('_', ' '),
                style = MicroStyle.copy(fontSize = 8.sp),
                color = IRL.TextFaint,
            )
        }
        LevelMeter(state.audioLevel)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("−60", style = MonoStyle.copy(fontSize = 8.sp), color = IRL.TextFaint)
            Spacer(Modifier.weight(1f))
            Text("0 dB", style = MonoStyle.copy(fontSize = 8.sp), color = IRL.TextFaint)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (settings.audio.muted) Icons.Rounded.MicOff else Icons.Rounded.GraphicEq,
                null,
                tint = if (settings.audio.muted) IRL.Danger else IRL.Signal,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (settings.audio.muted) "Muted" else "Mic open",
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.audio.muted) IRL.Danger else IRL.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = if (settings.audio.muted) "UNMUTE" else "MUTE",
                onClick = onToggleMute,
            )
        }
    }
}

@Composable
private fun CollapsedConsoleRail(modifier: Modifier = Modifier, onShow: () -> Unit) {
    val shape = CutCornerShape(topEnd = 10.dp)
    Column(
        modifier
            .clip(shape)
            .background(IRL.Panel)
            .border(1.dp, IRL.Line, shape)
            .clickable(onClick = onShow)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(18.dp).height(2.dp).background(IRL.Signal))
        Spacer(Modifier.height(10.dp))
        Icon(Icons.Rounded.Visibility, null, tint = IRL.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "C\nO\nN\nS\nO\nL\nE",
            style = MicroStyle.copy(fontSize = 8.sp, lineHeight = 11.sp),
            color = IRL.TextFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CameraPickerSheet(
    vm: AppViewModel,
    settings: AppSettings,
    state: EngineState,
    onDismiss: () -> Unit,
    onSelectBuiltIn: (BuiltInCameraInfo) -> Unit,
    onSelectUvc: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    SheetContent(
        onDismiss = onDismiss,
        title = "Camera source",
        subtitle = "Use the built-in cameras or any UVC webcam connected over USB.",
    ) {
        SectionHeader("01", "Built-in cameras") {
            Text(
                "${state.builtInCameras.size} FOUND",
                style = MonoStyle.copy(fontSize = 8.sp),
                color = IRL.TextFaint,
            )
        }
        if (state.builtInCameras.isEmpty()) {
            InfoBanner(
                text = "No built-in cameras reported yet. Restart the app or check the camera permission.",
                icon = Icons.Rounded.Warning,
                color = IRL.Warning,
            )
        } else {
            state.builtInCameras.forEach { camera ->
                CameraOption(
                    title = camera.label,
                    subtitle = "Camera ${camera.id} · up to ${camera.maxWidth}×${camera.maxHeight}",
                    icon = if (camera.facing == LensFacing.FRONT) Icons.Rounded.Cameraswitch else Icons.Rounded.Videocam,
                    selected = settings.camera.kind == CameraKind.BUILT_IN && (
                        settings.camera.cameraId == camera.id ||
                            (settings.camera.cameraId == null && settings.camera.lens == camera.facing)
                        ),
                    onClick = { onSelectBuiltIn(camera) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        SectionHeader("02", "UVC / USB webcam") {
            Text(
                "REFRESH",
                style = MicroStyle.copy(fontSize = 8.sp),
                color = IRL.Signal,
                modifier = Modifier
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            )
        }
        if (state.uvcDevices.isEmpty()) {
            InfoBanner(
                text = "No UVC camera detected. Connect a webcam or put your action camera in webcam mode, then refresh.",
                icon = Icons.Rounded.Usb,
                color = IRL.TextMuted,
            )
        } else {
            state.uvcDevices.forEach { device ->
                CameraOption(
                    title = device.name,
                    subtitle = "USB %04X:%04X · generic UVC".format(device.vendorId, device.productId),
                    icon = Icons.Rounded.Usb,
                    selected = settings.camera.kind == CameraKind.USB_UVC && settings.camera.uvcDeviceKey == device.key,
                    onClick = { onSelectUvc(device.key) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingSwitchRow(
            title = "Fall back to built-in camera",
            subtitle = "If the UVC camera is unplugged during a stream",
            checked = settings.camera.fallbackToBuiltIn,
            onCheckedChange = { enabled -> vm.updateCamera { it.copy(fallbackToBuiltIn = enabled) } },
        )
        SettingSwitchRow(
            title = "Mirror front camera",
            subtitle = "Flip the preview for selfie framing",
            checked = settings.camera.mirrorFrontCamera,
            onCheckedChange = { enabled -> vm.updateCamera { it.copy(mirrorFrontCamera = enabled) } },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "UVC support is generic. RealStreamer talks standard USB Video Class — there are no camera-specific drivers.",
            style = MaterialTheme.typography.bodySmall,
            color = IRL.TextFaint,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CameraOption(
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
            .background(if (selected) IRL.SignalFaint else IRL.Panel)
            .border(1.dp, if (selected) IRL.Signal.copy(alpha = 0.45f) else IRL.Line, shape)
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(
                        color = IRL.Signal,
                        size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) IRL.Signal else IRL.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = IRL.TextMuted, maxLines = 1)
        }
        if (selected) {
            val checkShape = CutCornerShape(topEnd = 4.dp)
            Box(
                Modifier
                    .size(18.dp)
                    .clip(checkShape)
                    .background(IRL.Signal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = IRL.Ink, modifier = Modifier.size(13.dp))
            }
        }
    }
}
