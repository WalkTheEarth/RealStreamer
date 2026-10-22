package com.irlstream.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class Orientation { LANDSCAPE, PORTRAIT }

@Serializable
data class VideoSettings(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 3500,
    val keyframeIntervalSec: Int = 2,
) {
    val orientation: Orientation get() = if (width >= height) Orientation.LANDSCAPE else Orientation.PORTRAIT
    val display: String get() = "${width}×${height} · ${fps}fps · ${"%.1f".format(bitrateKbps / 1000f)} Mbps"
    val aspect: Float get() = width.toFloat() / height.toFloat()
}

@Serializable
enum class AudioInput {
    VOICE_COMMUNICATION,
    CAMCORDER,
    MIC,
    VOICE_RECOGNITION,
}

@Serializable
data class AudioSettings(
    val bitrateKbps: Int = 128,
    val channels: Int = 1,
    val sampleRate: Int = 48_000,
    val input: AudioInput = AudioInput.VOICE_COMMUNICATION,
    val echoCancellation: Boolean = true,
    val noiseSuppression: Boolean = true,
    val autoGain: Boolean = false,
    val muted: Boolean = false,
)

@Serializable
enum class CameraKind { BUILT_IN, USB_UVC }

@Serializable
enum class LensFacing { BACK, FRONT }

@Serializable
data class CameraSettings(
    val kind: CameraKind = CameraKind.BUILT_IN,
    val lens: LensFacing = LensFacing.BACK,
    /**
     * Specific Camera2 camera ID, e.g. "0", "1", "2". Modern phones expose several
     * physical cameras behind one logical camera; null keeps the lens-based default.
     */
    val cameraId: String? = null,
    /** Stable key of a UVC device; null means "first / only connected camera". */
    val uvcDeviceKey: String? = null,
    val mirrorFrontCamera: Boolean = true,
    /** Extra clockwise rotation after automatic sensor correction: 0, 90, 180, 270. */
    val rotationOffsetDegrees: Int = 0,
    /** Flip the current camera horizontally, on top of the front-camera default. */
    val mirrorImage: Boolean = false,
    /**
     * Keep the whole camera frame (letterboxed) instead of cropping it to fill the
     * program frame. Fit is the default so a manual 90°/270° rotation never zooms.
     */
    val fitCamera: Boolean = true,
    /** Auto-fallback to the built-in camera if the UVC device disappears. */
    val fallbackToBuiltIn: Boolean = true,
)

@Serializable
enum class PlatformPreset { CUSTOM, TWITCH, YOUTUBE, KICK }

@Serializable
data class Destination(
    val id: String = newId(),
    val name: String = "",
    /** Full ingest URL including the stream key, e.g. rtmp://live.twitch.tv/app/live_xxx */
    val url: String = "",
    val enabled: Boolean = true,
    val preset: PlatformPreset = PlatformPreset.CUSTOM,
    val autoReconnect: Boolean = true,
) {
    val displayName: String
        get() = name.ifBlank {
            when (preset) {
                PlatformPreset.TWITCH -> "Twitch"
                PlatformPreset.YOUTUBE -> "YouTube"
                PlatformPreset.KICK -> "Kick"
                PlatformPreset.CUSTOM -> "Custom RTMP"
            }
        }
}

@Serializable
enum class SourceAudience { BROADCAST, STREAMER_ONLY }

@Serializable
enum class SourceType {
    TEXT,
    CHAT,
    STATS,
    WEB,
    IMAGE,
    STREAMER_CHAT,
    STREAMER_STATS,
    STREAMER_OUTPUTS,
    STREAMER_AUDIO,
    ;

    val isStreamerOnly: Boolean
        get() = this == STREAMER_CHAT || this == STREAMER_STATS ||
            this == STREAMER_OUTPUTS || this == STREAMER_AUDIO

    val audience: SourceAudience
        get() = if (isStreamerOnly) SourceAudience.STREAMER_ONLY else SourceAudience.BROADCAST
}

@Serializable
enum class TextAlignment { START, CENTER, END }

@Serializable
enum class ImageFit { CONTAIN, COVER }

@Serializable
enum class ConsolePanel { CHAT, STATS, OUTPUTS, AUDIO }

@Serializable
data class NormalizedRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    fun clamped(): NormalizedRect {
        val w = width.coerceIn(0.02f, 1f)
        val h = height.coerceIn(0.02f, 1f)
        return copy(
            x = x.coerceIn(0f, 1f - w),
            y = y.coerceIn(0f, 1f - h),
            width = w,
            height = h,
        )
    }

    fun contains(nx: Float, ny: Float): Boolean =
        nx >= x && nx <= x + width && ny >= y && ny <= y + height

    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

@Serializable
sealed class SourceConfig {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String = "LIVE FROM THE STREETS",
        val fontSizeSp: Int = 48,
        val colorArgb: Long = 0xFFFFFFFF,
        val backgroundArgb: Long = 0x99000000,
        val alignment: TextAlignment = TextAlignment.START,
        val bold: Boolean = true,
        val paddingDp: Int = 16,
        /** Replaces {time}, {uptime}, {fps}, {bitrate} tokens when true. */
        val dynamic: Boolean = true,
    ) : SourceConfig()

    @Serializable
    @SerialName("chat")
    data class Chat(
        val maxMessages: Int = 7,
        val fontSizeSp: Int = 26,
        val panelArgb: Long = 0x8C070B09,
        val textArgb: Long = 0xFFF4FFF8,
        val showNames: Boolean = true,
        val showTimestamps: Boolean = false,
        val hideCommandMessages: Boolean = true,
        val accentArgb: Long = 0xFF39FF88,
    ) : SourceConfig()

    @Serializable
    @SerialName("stats")
    data class Stats(
        val fontSizeSp: Int = 24,
        val colorArgb: Long = 0xFFE9FFF0,
        val panelArgb: Long = 0x99070B09,
        val accentArgb: Long = 0xFF39FF88,
        val showBitrate: Boolean = true,
        val showFps: Boolean = true,
        val showDropped: Boolean = true,
        val showUptime: Boolean = true,
        val showDestinations: Boolean = true,
    ) : SourceConfig()

    @Serializable
    @SerialName("web")
    data class Web(
        val url: String = "https://",
        val transparent: Boolean = true,
        val zoomPercent: Int = 100,
        val reloadIntervalSec: Int = 0,
        val userAgent: String = "",
    ) : SourceConfig()

    @Serializable
    @SerialName("image")
    data class Image(
        val uri: String = "",
        val fit: ImageFit = ImageFit.CONTAIN,
    ) : SourceConfig()

    @Serializable
    @SerialName("console")
    data class Console(
        val panel: ConsolePanel = ConsolePanel.CHAT,
    ) : SourceConfig()
}

@Serializable
data class SceneSource(
    val id: String = newId(),
    val type: SourceType,
    val name: String,
    val enabled: Boolean = true,
    val audience: SourceAudience,
    val rect: NormalizedRect = NormalizedRect(),
    val order: Int = 0,
    val config: SourceConfig,
)

@Serializable
data class Scene(
    val backgroundArgb: Long = 0xFF000000,
    val sources: List<SceneSource> = defaultSources(),
) {
    val broadcastSources: List<SceneSource>
        get() = sources.filter { it.audience == SourceAudience.BROADCAST }.sortedBy { it.order }

    val streamerSources: List<SceneSource>
        get() = sources.filter { it.audience == SourceAudience.STREAMER_ONLY }.sortedBy { it.order }

    fun source(id: String): SceneSource? = sources.firstOrNull { it.id == id }

    fun upsert(source: SceneSource): Scene =
        copy(sources = sources.filterNot { it.id == source.id } + source)

    fun remove(id: String): Scene = copy(sources = sources.filterNot { it.id == id })

    fun nextOrder(audience: SourceAudience): Int =
        (sources.filter { it.audience == audience }.maxOfOrNull { it.order } ?: -1) + 1
}

@Serializable
data class ChatSettings(
    val twitchChannel: String = "",
    /** Optional account name used together with an OAuth token. */
    val twitchUsername: String = "",
    /** Optional OAuth token (without the "oauth:" prefix) for channels that require auth. */
    val twitchToken: String = "",
    val customWebSocketUrl: String = "",
    val customUserField: String = "user",
    val customMessageField: String = "message",
    val customColorField: String = "color",
    /** Emits local sample messages — useful for positioning chat overlays. */
    val demoMessages: Boolean = false,
    val hideCommands: Boolean = true,
) {
    val hasTwitch: Boolean get() = twitchChannel.isNotBlank()
    val hasCustom: Boolean get() = customWebSocketUrl.isNotBlank()
}

@Serializable
data class ConsoleSettings(
    val visible: Boolean = true,
    val widthDp: Int = 320,
)

@Serializable
data class AppSettings(
    val schemaVersion: Int = 1,
    val onboardingComplete: Boolean = false,
    val video: VideoSettings = VideoSettings(),
    val audio: AudioSettings = AudioSettings(),
    val camera: CameraSettings = CameraSettings(),
    val destinations: List<Destination> = emptyList(),
    val scene: Scene = Scene(),
    val chat: ChatSettings = ChatSettings(),
    val console: ConsoleSettings = ConsoleSettings(),
) {
    val activeDestinations: List<Destination> get() = destinations.filter { it.enabled }
    val hasLiveOutput: Boolean get() = activeDestinations.isNotEmpty()
}

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

object Sources {

    fun defaultSources(): List<SceneSource> = listOf(
        streamerConsole(ConsolePanel.CHAT, 0),
        streamerConsole(ConsolePanel.STATS, 1),
        streamerConsole(ConsolePanel.OUTPUTS, 2),
        streamerConsole(ConsolePanel.AUDIO, 3),
    )

    fun streamerConsole(panel: ConsolePanel, order: Int): SceneSource {
        val type = when (panel) {
            ConsolePanel.CHAT -> SourceType.STREAMER_CHAT
            ConsolePanel.STATS -> SourceType.STREAMER_STATS
            ConsolePanel.OUTPUTS -> SourceType.STREAMER_OUTPUTS
            ConsolePanel.AUDIO -> SourceType.STREAMER_AUDIO
        }
        val name = when (panel) {
            ConsolePanel.CHAT -> "Chat (streamer only)"
            ConsolePanel.STATS -> "Stream stats (streamer only)"
            ConsolePanel.OUTPUTS -> "Output status (streamer only)"
            ConsolePanel.AUDIO -> "Audio meter (streamer only)"
        }
        return SceneSource(
            type = type,
            name = name,
            audience = SourceAudience.STREAMER_ONLY,
            order = order,
            config = SourceConfig.Console(panel),
        )
    }

    fun broadcast(type: SourceType, order: Int): SceneSource {
        require(!type.isStreamerOnly) { "$type is not a broadcast source" }
        return when (type) {
            SourceType.TEXT -> SceneSource(
                type = type,
                name = "Text",
                audience = SourceAudience.BROADCAST,
                rect = NormalizedRect(0.04f, 0.70f, 0.80f, 0.20f),
                order = order,
                config = SourceConfig.Text(),
            )
            SourceType.CHAT -> SceneSource(
                type = type,
                name = "Chat (on stream)",
                audience = SourceAudience.BROADCAST,
                rect = NormalizedRect(0.67f, 0.04f, 0.29f, 0.62f),
                order = order,
                config = SourceConfig.Chat(),
            )
            SourceType.STATS -> SceneSource(
                type = type,
                name = "Stats (on stream)",
                audience = SourceAudience.BROADCAST,
                rect = NormalizedRect(0.04f, 0.04f, 0.34f, 0.18f),
                order = order,
                config = SourceConfig.Stats(),
            )
            SourceType.WEB -> SceneSource(
                type = type,
                name = "Web source",
                audience = SourceAudience.BROADCAST,
                rect = NormalizedRect(0.06f, 0.20f, 0.52f, 0.60f),
                order = order,
                config = SourceConfig.Web(),
            )
            SourceType.IMAGE -> SceneSource(
                type = type,
                name = "Image / logo",
                audience = SourceAudience.BROADCAST,
                rect = NormalizedRect(0.04f, 0.04f, 0.22f, 0.22f),
                order = order,
                config = SourceConfig.Image(),
            )
            else -> error("Unsupported broadcast source $type")
        }
    }
}

fun defaultSources(): List<SceneSource> = Sources.defaultSources()
