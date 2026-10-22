package com.irlstream.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun roundTripsFullSettingsWithEverySourceType() {
        val scene = Scene(
            sources = buildList {
                add(Sources.broadcast(SourceType.TEXT, 0))
                add(Sources.broadcast(SourceType.CHAT, 1))
                add(Sources.broadcast(SourceType.STATS, 2))
                add(Sources.broadcast(SourceType.WEB, 3))
                add(Sources.broadcast(SourceType.IMAGE, 4))
                add(Sources.streamerConsole(ConsolePanel.CHAT, 0))
                add(Sources.streamerConsole(ConsolePanel.STATS, 1))
                add(Sources.streamerConsole(ConsolePanel.OUTPUTS, 2))
                add(Sources.streamerConsole(ConsolePanel.AUDIO, 3))
            },
        )
        val settings = AppSettings(
            onboardingComplete = true,
            destinations = listOf(
                Destination(name = "Twitch", url = "rtmp://live.twitch.tv/app/live_123", preset = PlatformPreset.TWITCH),
                Destination(name = "Local", url = "rtmp://10.0.0.2:1935/live/key", enabled = false),
            ),
            scene = scene,
            video = VideoSettings(width = 1920, height = 1080, fps = 60, bitrateKbps = 6000),
            audio = AudioSettings(bitrateKbps = 160, channels = 2, muted = true),
            camera = CameraSettings(kind = CameraKind.USB_UVC, uvcDeviceKey = "1234:5678"),
            chat = ChatSettings(twitchChannel = "somechannel", customWebSocketUrl = "wss://example.com/chat"),
            console = ConsoleSettings(widthDp = 340),
        )

        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        assertTrue(encoded.contains("\"text\""))
        assertTrue(encoded.contains("\"console\""))
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings, decoded)
        assertEquals(9, decoded.scene.sources.size)
        assertEquals(2, decoded.destinations.size)
        assertEquals(SourceType.WEB, decoded.scene.sources.first { it.type == SourceType.WEB }.type)
    }

    @Test
    fun rectClampingKeepsLayersOnCanvas() {
        val rect = NormalizedRect(x = 0.95f, y = -0.2f, width = 0.5f, height = 0.1f).clamped()
        assertEquals(0.5f, rect.width, 0.0001f)
        assertEquals(0.1f, rect.height, 0.0001f)
        assertEquals(0.5f, rect.x, 0.0001f)
        assertEquals(0f, rect.y, 0.0001f)
        assertTrue(rect.x + rect.width <= 1.0001f)
        assertTrue(rect.y + rect.height <= 1.0001f)
    }

    @Test
    fun sceneUpsertRemoveAndOrdering() {
        var scene = Scene(sources = emptyList())
        val text = Sources.broadcast(SourceType.TEXT, scene.nextOrder(SourceAudience.BROADCAST))
        scene = scene.upsert(text)
        val chat = Sources.broadcast(SourceType.CHAT, scene.nextOrder(SourceAudience.BROADCAST))
        scene = scene.upsert(chat)
        assertEquals(listOf(text.id, chat.id), scene.broadcastSources.map { it.id })

        val moved = scene.upsert(text.copy(order = 5))
        assertEquals(listOf(chat.id, text.id), moved.broadcastSources.map { it.id })
        assertEquals(1, moved.remove(text.id).sources.size)
    }

    @Test
    fun destinationDefaultsAndMasking() {
        val destination = Destination(url = "rtmp://a.rtmp.youtube.com/live2/abcd-efgh-ijkl")
        assertEquals("Custom RTMP", destination.displayName)
        val parsed = com.irlstream.core.rtmp.RtmpUrl.parse(destination.url)!!
        assertEquals("live2", parsed.app)
        assertEquals("abcd-efgh-ijkl", parsed.streamName)
        assertTrue(parsed.masked().endsWith("ijkl"))
    }
}
