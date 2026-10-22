package com.irlstream.app.engine

import com.irlstream.core.model.CameraKind

enum class StreamPhase { IDLE, PREVIEW, LIVE, STOPPING }

enum class DestinationStatus { OFFLINE, CONNECTING, LIVE, RECONNECTING, ERROR, DISABLED }

enum class ChatPlatform { TWITCH, CUSTOM, DEMO }

data class ChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val colorArgb: Long,
    val platform: ChatPlatform,
    val timestampMs: Long,
)

data class DestinationRuntime(
    val id: String,
    val name: String,
    val maskedUrl: String,
    val status: DestinationStatus = DestinationStatus.OFFLINE,
    val message: String? = null,
    val bitrateKbps: Int = 0,
    val reconnectAttempts: Int = 0,
    val droppedFrames: Long = 0,
)

data class UvcDeviceInfo(
    val key: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
)

data class StatsSnapshot(
    val fps: Float = 0f,
    val bitrateKbps: Int = 0,
    val droppedFrames: Long = 0,
    val uptimeMs: Long = 0,
    val destinationsLive: Int = 0,
    val destinationsTotal: Int = 0,
    val videoLabel: String = "",
)

data class EngineState(
    val phase: StreamPhase = StreamPhase.IDLE,
    val liveStartedAtMs: Long? = null,
    val durationMs: Long = 0,
    val fps: Float = 0f,
    val videoBitrateKbps: Int = 0,
    val droppedFrames: Long = 0,
    val destinations: List<DestinationRuntime> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val chatStatus: String = "Chat offline",
    val audioLevel: Float = 0f,
    val audioMuted: Boolean = false,
    val cameraLabel: String = "Camera idle",
    val cameraKind: CameraKind? = null,
    val uvcDevices: List<UvcDeviceInfo> = emptyList(),
    val uvcAttached: Boolean = false,
    val resolution: String = "",
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val error: String? = null,
    val previewAttached: Boolean = false,
    val webErrors: Map<String, String> = emptyMap(),
    val pendingVideoSettings: Boolean = false,
    val pendingAudioSettings: Boolean = false,
) {
    val isLive: Boolean get() = phase == StreamPhase.LIVE
    val isBusy: Boolean get() = phase == StreamPhase.STOPPING
    val enabledDestinations: List<DestinationRuntime>
        get() = destinations.filter { it.status != DestinationStatus.DISABLED }

    fun statsSnapshot(): StatsSnapshot = StatsSnapshot(
        fps = fps,
        bitrateKbps = videoBitrateKbps,
        droppedFrames = droppedFrames,
        uptimeMs = durationMs,
        destinationsLive = destinations.count { it.status == DestinationStatus.LIVE },
        destinationsTotal = destinations.count { it.status != DestinationStatus.DISABLED },
        videoLabel = resolution,
    )
}
