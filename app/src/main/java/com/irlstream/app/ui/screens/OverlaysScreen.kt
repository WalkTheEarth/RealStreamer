package com.irlstream.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.engine.EngineState
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.ChoiceChip
import com.irlstream.app.ui.components.EditablePreview
import com.irlstream.app.ui.components.EmptyState
import com.irlstream.app.ui.components.findActivity
import com.irlstream.app.ui.components.IconAction
import com.irlstream.app.ui.components.InfoBanner
import com.irlstream.app.ui.components.LockStreamOrientation
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PrimaryButton
import com.irlstream.app.ui.components.ScreenTopBar
import com.irlstream.app.ui.components.SectionTitle
import com.irlstream.app.ui.components.Segmented
import com.irlstream.app.ui.components.SettingSwitchRow
import com.irlstream.app.ui.components.SheetContent
import com.irlstream.app.ui.components.TextFieldIRL
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.ConsolePanel
import com.irlstream.core.model.ImageFit
import com.irlstream.core.model.SceneSource
import com.irlstream.core.model.SourceAudience
import com.irlstream.core.model.SourceConfig
import com.irlstream.core.model.SourceType
import com.irlstream.core.model.TextAlignment
import java.io.File

@Composable
fun OverlaysScreen(vm: AppViewModel, settings: AppSettings, state: EngineState) {
    val activeWidth = if (state.outputWidth > 0) state.outputWidth else settings.video.width
    val activeHeight = if (state.outputHeight > 0) state.outputHeight else settings.video.height
    val activeVideo = settings.video.copy(width = activeWidth, height = activeHeight)
    LockStreamOrientation(activeVideo)
    var tab by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val scene = settings.scene
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val currentBroadcast = scene.broadcastSources
    val currentStreamer = scene.streamerSources

    LaunchedEffect(scene.sources) {
        if (selectedId != null && scene.source(selectedId!!) == null) selectedId = null
        if (editorId != null && scene.source(editorId!!) == null) editorId = null
    }

    AppBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = "Overlays",
                subtitle = "Broadcast layers and local-only streamer panels",
                onBack = { vm.back() },
                actions = {
                    IconAction(Icons.Rounded.Add, "Add source", { addSheet = true })
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Segmented(
                    options = listOf("On stream", "Streamer only"),
                    selected = if (tab == 0) "On stream" else "Streamer only",
                    onSelect = { tab = if (it == "On stream") 0 else 1 },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                if (tab == 0) {
                    if (state.isLive) {
                        InfoBanner(
                            text = "You are live. Layer changes are composited into the outgoing stream immediately.",
                            icon = Icons.Rounded.Warning,
                            color = IRL.Warning,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    EditablePreview(
                        aspectRatio = activeWidth.toFloat() / activeHeight,
                        sources = currentBroadcast,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        onRectChange = { id, rect ->
                            scene.source(id)?.let { source ->
                                vm.updateSource(source.copy(rect = rect.clamped()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Drag a layer to move it. Drag the bottom-right handle to resize. The preview is the broadcast.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = IRL.TextFaint,
                    )
                    SectionTitle("Broadcast layers") {
                        Text(
                            "ADD",
                            style = MicroStyle.copy(fontSize = 8.sp),
                            color = IRL.Neon,
                            modifier = Modifier.clickable { addSheet = true },
                        )
                    }
                    if (currentBroadcast.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = IRL.Panel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IRL.Line),
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.FormatSize,
                                title = "The camera is the whole scene",
                                body = "Add text, chat, stats, a web widget, or a logo. These are the layers viewers see.",
                                actionLabel = "Add on-stream source",
                                onAction = { addSheet = true },
                            )
                        }
                    } else {
                        currentBroadcast.sortedByDescending { it.order }.forEach { source ->
                            SourceRow(
                                source = source,
                                selected = source.id == selectedId,
                                onSelect = { selectedId = source.id },
                                onEdit = { editorId = source.id },
                                onToggle = { vm.toggleSource(source.id, it) },
                                onMoveUp = { vm.moveSource(source.id, +1) },
                                onMoveDown = { vm.moveSource(source.id, -1) },
                                onDelete = { deleteId = source.id },
                            )
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Add on-stream source",
                        icon = Icons.Rounded.Add,
                        onClick = { addSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = IRL.Panel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IRL.Line),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(IRL.NeonFaint),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Visibility, null, tint = IRL.Neon, modifier = Modifier.size(15.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Streamer only", style = MaterialTheme.typography.titleSmall, color = IRL.Text)
                                    Text(
                                        "Rendered in your Studio sidebar. Never encoded into the stream.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = IRL.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                    SectionTitle("Streamer panels") {
                        Text(
                            "ADD",
                            style = MicroStyle.copy(fontSize = 8.sp),
                            color = IRL.Neon,
                            modifier = Modifier.clickable { addSheet = true },
                        )
                    }
                    if (currentStreamer.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.Chat,
                            title = "No streamer panels",
                            body = "Add a private chat reader, stream stats, output status, or an audio meter.",
                        )
                    } else {
                        currentStreamer.sortedBy { it.order }.forEach { source ->
                            SourceRow(
                                source = source,
                                selected = false,
                                onSelect = { editorId = source.id },
                                onEdit = { editorId = source.id },
                                onToggle = { vm.toggleSource(source.id, it) },
                                onMoveUp = { vm.moveSource(source.id, -1) },
                                onMoveDown = { vm.moveSource(source.id, +1) },
                                onDelete = { deleteId = source.id },
                                streamer = true,
                            )
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    MicroLabel("Quick add", IRL.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    val existingPanels = currentStreamer.mapNotNull { (it.config as? SourceConfig.Console)?.panel }.toSet()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ConsolePanel.entries.forEach { panel ->
                            ChoiceChip(
                                text = panel.label,
                                selected = panel in existingPanels,
                                enabled = panel !in existingPanels,
                                onClick = { vm.addStreamerPanel(panel) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (addSheet) {
        AddSourceSheet(
            onDismiss = { addSheet = false },
            onAddBroadcast = { type ->
                val id = vm.addBroadcastSource(type)
                selectedId = id
                editorId = id
                addSheet = false
            },
            onAddStreamer = { panel ->
                vm.addStreamerPanel(panel)
                addSheet = false
            },
        )
    }

    val editorSource = editorId?.let { scene.source(it) }
    if (editorSource != null) {
        SourceEditorSheet(
            vm = vm,
            source = editorSource,
            isLive = state.isLive,
            webError = state.webErrors[editorSource.id],
            onDismiss = { editorId = null },
            onUpdate = { updated -> vm.updateSource(updated) },
            onDelete = {
                editorId = null
                deleteId = editorSource.id
            },
        )
    }

    val deleteTarget = deleteId?.let { scene.source(it) }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = IRL.InkRaised,
            titleContentColor = IRL.Text,
            textContentColor = IRL.TextMuted,
            title = { Text("Remove layer?") },
            text = { Text("\"${deleteTarget.name}\" will be removed from the scene.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSource(deleteTarget.id)
                    if (selectedId == deleteTarget.id) selectedId = null
                    deleteId = null
                }) {
                    Text("Remove", color = IRL.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel", color = IRL.TextMuted) }
            },
        )
    }
}

@Composable
private fun SourceRow(
    source: SceneSource,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    streamer: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) IRL.NeonFaint else IRL.Panel)
            .border(
                1.dp,
                if (selected) IRL.Neon.copy(alpha = 0.4f) else IRL.Line,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IRL.PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sourceIcon(source.type), null, tint = if (source.enabled) IRL.Neon else IRL.TextFaint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (source.enabled) IRL.Text else IRL.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sourceSubtitle(source, streamer),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = IRL.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(
            icon = if (source.enabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (source.enabled) "Hide" else "Show",
            onClick = { onToggle(!source.enabled) },
            background = Color.Transparent,
            border = false,
            size = 28,
            iconSize = 15,
            tint = if (source.enabled) IRL.TextSecondary else IRL.TextFaint,
        )
        IconAction(Icons.Rounded.ArrowUpward, "Move up", onMoveUp, background = Color.Transparent, border = false, size = 28, iconSize = 14, tint = IRL.TextFaint)
        IconAction(Icons.Rounded.ArrowDownward, "Move down", onMoveDown, background = Color.Transparent, border = false, size = 28, iconSize = 14, tint = IRL.TextFaint)
        IconAction(Icons.Rounded.Edit, "Edit", onEdit, background = Color.Transparent, border = false, size = 28, iconSize = 14, tint = IRL.TextMuted)
        IconAction(Icons.Rounded.Delete, "Delete", onDelete, background = Color.Transparent, border = false, size = 28, iconSize = 14, tint = IRL.Danger.copy(alpha = 0.8f))
    }
}

@Composable
private fun AddSourceSheet(
    onDismiss: () -> Unit,
    onAddBroadcast: (SourceType) -> Unit,
    onAddStreamer: (ConsolePanel) -> Unit,
) {
    SheetContent(
        onDismiss = onDismiss,
        title = "Add overlay",
        subtitle = "Broadcast layers are visible to viewers. Streamer panels are private.",
    ) {
        MicroLabel("On stream", IRL.Neon)
        Spacer(Modifier.height(8.dp))
        listOf(
            SourceType.TEXT to "Text",
            SourceType.CHAT to "Chat overlay",
            SourceType.STATS to "Stats overlay",
            SourceType.WEB to "Web source",
            SourceType.IMAGE to "Image or logo",
        ).forEach { (type, label) ->
            AddCard(
                icon = sourceIcon(type),
                title = label,
                description = broadcastDescription(type),
                onClick = { onAddBroadcast(type) },
            )
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(16.dp))
        MicroLabel("Streamer only", IRL.Warning)
        Spacer(Modifier.height(8.dp))
        ConsolePanel.entries.forEach { panel ->
            AddCard(
                icon = sourceIcon(
                    when (panel) {
                        ConsolePanel.CHAT -> SourceType.STREAMER_CHAT
                        ConsolePanel.STATS -> SourceType.STREAMER_STATS
                        ConsolePanel.OUTPUTS -> SourceType.STREAMER_OUTPUTS
                        ConsolePanel.AUDIO -> SourceType.STREAMER_AUDIO
                    },
                ),
                title = panel.label,
                description = consoleDescription(panel),
                onClick = { onAddStreamer(panel) },
                tint = IRL.Warning,
            )
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun AddCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    tint: Color = IRL.Neon,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IRL.Panel)
            .border(1.dp, IRL.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text)
            Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = IRL.TextMuted)
        }
        Icon(Icons.Rounded.Add, null, tint = IRL.TextFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SourceEditorSheet(
    vm: AppViewModel,
    source: SceneSource,
    isLive: Boolean,
    webError: String?,
    onDismiss: () -> Unit,
    onUpdate: (SceneSource) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(source.id) { mutableStateOf(source.name) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.importImage(uri) { path ->
                if (path != null) {
                    val cfg = source.config as? SourceConfig.Image ?: SourceConfig.Image()
                    onUpdate(source.copy(config = cfg.copy(uri = path)))
                } else {
                    vm.showToast("Could not import that image")
                }
            }
        }
    }

    SheetContent(
        onDismiss = onDismiss,
        title = source.name,
        subtitle = if (source.audience == SourceAudience.BROADCAST) {
            "On-stream layer · composited live"
        } else {
            "Streamer-only panel · never broadcast"
        },
    ) {
        if (isLive && source.audience == SourceAudience.BROADCAST) {
            InfoBanner("Live edits are visible to viewers immediately.", icon = Icons.Rounded.Warning)
            Spacer(Modifier.height(12.dp))
        }
        TextFieldIRL(
            value = name,
            onValueChange = {
                name = it
                onUpdate(source.copy(name = it))
            },
            label = "Layer name",
        )
        Spacer(Modifier.height(10.dp))
        SettingSwitchRow(
            title = "Visible",
            subtitle = if (source.audience == SourceAudience.BROADCAST) "Include this layer in the broadcast" else "Show this panel in the Studio sidebar",
            checked = source.enabled,
            onCheckedChange = { onUpdate(source.copy(enabled = it)) },
        )
        Spacer(Modifier.height(6.dp))

        when (val config = source.config) {
            is SourceConfig.Text -> TextEditor(config, isLive) { updated ->
                onUpdate(source.copy(config = updated))
            }
            is SourceConfig.Chat -> ChatEditor(config) { updated ->
                onUpdate(source.copy(config = updated))
            }
            is SourceConfig.Stats -> StatsEditor(config) { updated ->
                onUpdate(source.copy(config = updated))
            }
            is SourceConfig.Web -> WebEditor(config, webError) { updated ->
                onUpdate(source.copy(config = updated))
            }
            is SourceConfig.Image -> ImageEditor(
                config = config,
                onUpdate = { updated -> onUpdate(source.copy(config = updated)) },
                onPick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            is SourceConfig.Console -> Unit
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDelete)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Delete, null, tint = IRL.Danger, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete layer", style = MaterialTheme.typography.labelMedium, color = IRL.Danger)
        }
    }
}

@Composable
private fun TextEditor(config: SourceConfig.Text, isLive: Boolean, onChange: (SourceConfig.Text) -> Unit) {
    var text by remember(config.text) { mutableStateOf(config.text) }
    TextFieldIRL(
        value = text,
        onValueChange = {
            text = it
            onChange(config.copy(text = it))
        },
        label = "Text",
        singleLine = false,
        minLines = 2,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Tokens: {time} {uptime} {fps} {bitrate} {resolution} {destinations}",
        style = MonoStyle.copy(fontSize = 9.sp),
        color = IRL.TextFaint,
    )
    SliderSetting(
        label = "Font size",
        value = config.fontSizeSp.toFloat(),
        range = 16f..96f,
        labelValue = "${config.fontSizeSp} px",
    ) { onChange(config.copy(fontSizeSp = it.toInt())) }
    Swatches("Text color", textColors, config.colorArgb) { onChange(config.copy(colorArgb = it)) }
    Swatches("Backdrop", backdropColors, config.backgroundArgb) { onChange(config.copy(backgroundArgb = it)) }
    Spacer(Modifier.height(6.dp))
    MicroLabel("Alignment", IRL.TextMuted)
    Spacer(Modifier.height(6.dp))
    Segmented(
        options = listOf("Left", "Center", "Right"),
        selected = when (config.alignment) {
            TextAlignment.START -> "Left"
            TextAlignment.CENTER -> "Center"
            TextAlignment.END -> "Right"
        },
        onSelect = {
            onChange(
                config.copy(
                    alignment = when (it) {
                        "Center" -> TextAlignment.CENTER
                        "Right" -> TextAlignment.END
                        else -> TextAlignment.START
                    },
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    SettingSwitchRow("Bold", checked = config.bold, onCheckedChange = { onChange(config.copy(bold = it)) })
    SettingSwitchRow("Dynamic tokens", subtitle = "Update time, uptime, and stats every second", checked = config.dynamic, onCheckedChange = { onChange(config.copy(dynamic = it)) })
}

@Composable
private fun ChatEditor(config: SourceConfig.Chat, onChange: (SourceConfig.Chat) -> Unit) {
    SliderSetting(
        label = "Messages shown",
        value = config.maxMessages.toFloat(),
        range = 3f..12f,
        labelValue = "${config.maxMessages}",
    ) { onChange(config.copy(maxMessages = it.toInt())) }
    SliderSetting(
        label = "Font size",
        value = config.fontSizeSp.toFloat(),
        range = 16f..48f,
        labelValue = "${config.fontSizeSp} px",
    ) { onChange(config.copy(fontSizeSp = it.toInt())) }
    Swatches("Accent", textColors, config.accentArgb) { onChange(config.copy(accentArgb = it)) }
    Swatches("Panel", panelColors, config.panelArgb) { onChange(config.copy(panelArgb = it)) }
    SettingSwitchRow("Show names", checked = config.showNames, onCheckedChange = { onChange(config.copy(showNames = it)) })
    SettingSwitchRow("Show timestamps", checked = config.showTimestamps, onCheckedChange = { onChange(config.copy(showTimestamps = it)) })
    SettingSwitchRow("Hide command messages", subtitle = "Skip messages starting with !", checked = config.hideCommandMessages, onCheckedChange = { onChange(config.copy(hideCommandMessages = it)) })
}

@Composable
private fun StatsEditor(config: SourceConfig.Stats, onChange: (SourceConfig.Stats) -> Unit) {
    SliderSetting(
        label = "Font size",
        value = config.fontSizeSp.toFloat(),
        range = 16f..44f,
        labelValue = "${config.fontSizeSp} px",
    ) { onChange(config.copy(fontSizeSp = it.toInt())) }
    Swatches("Accent", textColors, config.accentArgb) { onChange(config.copy(accentArgb = it)) }
    Swatches("Panel", panelColors, config.panelArgb) { onChange(config.copy(panelArgb = it)) }
    SettingSwitchRow("FPS", checked = config.showFps, onCheckedChange = { onChange(config.copy(showFps = it)) })
    SettingSwitchRow("Bitrate", checked = config.showBitrate, onCheckedChange = { onChange(config.copy(showBitrate = it)) })
    SettingSwitchRow("Dropped frames", checked = config.showDropped, onCheckedChange = { onChange(config.copy(showDropped = it)) })
    SettingSwitchRow("Uptime", checked = config.showUptime, onCheckedChange = { onChange(config.copy(showUptime = it)) })
    SettingSwitchRow("Output count", checked = config.showDestinations, onCheckedChange = { onChange(config.copy(showDestinations = it)) })
}

@Composable
private fun WebEditor(config: SourceConfig.Web, error: String?, onChange: (SourceConfig.Web) -> Unit) {
    var url by remember(config.url) { mutableStateOf(config.url) }
    TextFieldIRL(
        value = url,
        onValueChange = {
            url = it
            onChange(config.copy(url = it.trim()))
        },
        label = "Web page URL",
        placeholder = "https://widget.example.com/overlay",
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            autoCorrectEnabled = false,
        ),
        supportingText = "Renders a WebView into the scene. The page must be reachable from the phone.",
    )
    Spacer(Modifier.height(4.dp))
    SettingSwitchRow(
        title = "Transparent background",
        subtitle = "Preserves alpha via snapshot mode (~8 fps)",
        checked = config.transparent,
        onCheckedChange = { onChange(config.copy(transparent = it)) },
    )
    SliderSetting(
        label = "Zoom",
        value = config.zoomPercent.toFloat(),
        range = 50f..200f,
        labelValue = "${config.zoomPercent}%",
    ) { onChange(config.copy(zoomPercent = it.toInt())) }
    MicroLabel("Reload", IRL.TextMuted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 30, 60, 300).forEach { seconds ->
            ChoiceChip(
                text = if (seconds == 0) "Off" else "${seconds}s",
                selected = config.reloadIntervalSec == seconds,
                onClick = { onChange(config.copy(reloadIntervalSec = seconds)) },
            )
        }
    }
    if (!error.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(error, icon = Icons.Rounded.Warning, color = IRL.Warning)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Opaque pages use a virtual display for smooth animation. Transparent overlays use a lower-rate snapshot capture.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = IRL.TextFaint,
    )
}

@Composable
private fun ImageEditor(
    config: SourceConfig.Image,
    onUpdate: (SourceConfig.Image) -> Unit,
    onPick: () -> Unit,
) {
    if (config.uri.isNotBlank()) {
        Text(
            config.uri.substringAfterLast('/'),
            style = MonoStyle.copy(fontSize = 10.sp),
            color = IRL.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            text = if (config.uri.isBlank()) "Choose image" else "Replace image",
            onClick = onPick,
            modifier = Modifier.weight(1f),
        )
        if (config.uri.isNotBlank()) {
            androidx.compose.material3.OutlinedButton(
                onClick = { onUpdate(config.copy(uri = "")) },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Clear", color = IRL.TextMuted)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    MicroLabel("Fit", IRL.TextMuted)
    Spacer(Modifier.height(6.dp))
    Segmented(
        options = listOf("Contain", "Cover"),
        selected = if (config.fit == ImageFit.CONTAIN) "Contain" else "Cover",
        onSelect = { onUpdate(config.copy(fit = if (it == "Cover") ImageFit.COVER else ImageFit.CONTAIN)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Images are copied into app storage so the overlay survives restarts.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = IRL.TextFaint,
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    labelValue: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = IRL.Text, modifier = Modifier.weight(1f))
            Text(labelValue, style = MonoStyle.copy(fontSize = 11.sp), color = IRL.Neon)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = IRL.Neon,
                activeTrackColor = IRL.Neon,
                inactiveTrackColor = IRL.LineStrong,
            ),
        )
    }
}

@Composable
private fun Swatches(label: String, options: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Spacer(Modifier.height(8.dp))
    MicroLabel(label, IRL.TextMuted)
    Spacer(Modifier.height(7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        options.forEach { argb ->
            val color = Color(argb.toInt())
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (color.alpha == 0f) IRL.PanelHigh else color)
                    .border(
                        if (argb == selected) 2.dp else 1.dp,
                        if (argb == selected) IRL.Neon else IRL.LineStrong,
                        CircleShape,
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (color.alpha == 0f) {
                    Text("–", color = IRL.TextFaint, fontSize = 12.sp)
                }
                if (argb == selected) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = if (color.luminance() > 0.6f) IRL.Ink else IRL.Text,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun consoleDescription(panel: ConsolePanel): String = when (panel) {
    ConsolePanel.CHAT -> "Read chat while you stream — never on the broadcast."
    ConsolePanel.STATS -> "FPS, bitrate, dropped frames, and uptime."
    ConsolePanel.OUTPUTS -> "Per-destination connection status."
    ConsolePanel.AUDIO -> "Live input meter and mute control."
}

private val ConsolePanel.label: String
    get() = when (this) {
        ConsolePanel.CHAT -> "Chat"
        ConsolePanel.STATS -> "Stats"
        ConsolePanel.OUTPUTS -> "Outputs"
        ConsolePanel.AUDIO -> "Audio"
    }

private fun broadcastDescription(type: SourceType): String = when (type) {
    SourceType.TEXT -> "Static or dynamic text with tokens."
    SourceType.CHAT -> "Live chat rendered into the broadcast."
    SourceType.STATS -> "Show viewers your bitrate, FPS, and uptime."
    SourceType.WEB -> "Any web page or widget as a video source."
    SourceType.IMAGE -> "Logo, watermark, or full-frame graphic."
    else -> ""
}

private fun sourceIcon(type: SourceType): ImageVector = when (type) {
    SourceType.TEXT -> Icons.Rounded.FormatSize
    SourceType.CHAT, SourceType.STREAMER_CHAT -> Icons.Rounded.Chat
    SourceType.STATS, SourceType.STREAMER_STATS -> Icons.Rounded.BarChart
    SourceType.WEB -> Icons.Rounded.Public
    SourceType.IMAGE -> Icons.Rounded.Image
    SourceType.STREAMER_OUTPUTS -> Icons.Rounded.Podcasts
    SourceType.STREAMER_AUDIO -> Icons.Rounded.GraphicEq
}

private fun sourceSubtitle(source: SceneSource, streamer: Boolean): String = when (source.type) {
    SourceType.TEXT -> "Text · ${(source.config as? SourceConfig.Text)?.text?.take(30) ?: ""}"
    SourceType.CHAT -> "On-stream chat"
    SourceType.STATS -> "On-stream stats"
    SourceType.WEB -> (source.config as? SourceConfig.Web)?.url?.take(42) ?: "Web source"
    SourceType.IMAGE -> "Image overlay"
    SourceType.STREAMER_CHAT -> "Private chat reader"
    SourceType.STREAMER_STATS -> "Private stream stats"
    SourceType.STREAMER_OUTPUTS -> "Private output status"
    SourceType.STREAMER_AUDIO -> "Private audio meter"
}

private val textColors = listOf(
    0xFFFFFFFF, 0xFF39FF88, 0xFF050806, 0xFFFF5E6E,
    0xFFFFC857, 0xFF6EA8FF, 0xFFB48CFF,
)

private val backdropColors = listOf(
    0x00000000, 0x66000000, 0x99000000, 0xCC000000, 0xCC070B09,
)

private val panelColors = listOf(
    0x00000000, 0x66070B09, 0x8C070B09, 0xCC070B09,
)
