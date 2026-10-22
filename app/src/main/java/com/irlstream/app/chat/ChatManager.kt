package com.irlstream.app.chat

import com.irlstream.app.engine.ChatMessage
import com.irlstream.app.engine.ChatPlatform
import com.irlstream.core.model.ChatSettings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Merges chat from Twitch IRC (read-only), a generic JSON WebSocket, and local demo
 * messages. Chat feeds both the streamer console and any broadcast chat overlays.
 */
class ChatManager(
    private val scope: CoroutineScope,
    private val onMessage: (ChatMessage) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile private var settings = ChatSettings()
    @Volatile private var running = false
    private var sourceJobs = mutableListOf<Job>()
    private val statuses = HashMap<String, String>()
    private val nicknameCounter = AtomicInteger(1000)

    fun start(settings: ChatSettings) {
        running = true
        this.settings = settings
        restart()
    }

    fun update(settings: ChatSettings) {
        if (settings == this.settings) return
        this.settings = settings
        if (running) restart()
    }

    fun stop() {
        running = false
        sourceJobs.forEach { it.cancel() }
        sourceJobs.clear()
        statuses.clear()
        onStatus("Chat offline")
    }

    private fun restart() {
        sourceJobs.forEach { it.cancel() }
        sourceJobs.clear()
        statuses.clear()
        val current = settings

        if (current.hasTwitch) {
            sourceJobs += scope.launch(Dispatchers.IO) { twitchLoop(current) }
        }
        if (current.hasCustom) {
            sourceJobs += scope.launch(Dispatchers.IO) { customLoop(current) }
        }
        if (current.demoMessages) {
            sourceJobs += scope.launch(Dispatchers.IO) { demoLoop() }
        }
        if (!current.hasTwitch && !current.hasCustom && !current.demoMessages) {
            statuses["main"] = "Chat offline"
        }
        publishStatus()
    }

    // -----------------------------------------------------------------------
    // Twitch IRC over WebSocket
    // -----------------------------------------------------------------------

    private suspend fun twitchLoop(config: ChatSettings) {
        var backoff = 2_000L
        while (running && config == settings && config.hasTwitch) {
            try {
                connectTwitch(config)
                backoff = 2_000L
            } catch (_: Throwable) {
            }
            if (!running || config != settings) break
            setStatus("twitch", "reconnecting…")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
        setStatus("twitch", "offline")
    }

    private suspend fun connectTwitch(config: ChatSettings) = suspendCancellableCoroutine<Unit> { cont ->
        val channel = config.twitchChannel.trim().removePrefix("#").lowercase()
        if (channel.isBlank()) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                setStatus("twitch", "connecting…")
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                if (config.twitchToken.isNotBlank() && config.twitchUsername.isNotBlank()) {
                    webSocket.send("PASS oauth:${config.twitchToken.trim().removePrefix("oauth:")}")
                    webSocket.send("NICK ${config.twitchUsername.trim().lowercase()}")
                } else {
                    webSocket.send("PASS SCHMOOPIIE")
                    webSocket.send("NICK justinfan${nicknameCounter.incrementAndGet()}")
                }
                webSocket.send("JOIN #$channel")
                setStatus("twitch", "connected to #$channel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                for (line in text.split("\r\n", "\n")) {
                    if (line.isBlank()) continue
                    if (line.startsWith("PING ")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                        continue
                    }
                    if (line == "RECONNECT" || line.startsWith(":tmi.twitch.tv RECONNECT")) {
                        webSocket.close(1000, "reconnect requested")
                        continue
                    }
                    parseTwitchLine(line)?.let(onMessage)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                setStatus("twitch", "error: ${t.message ?: "connection failed"}")
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (cont.isActive) cont.resume(Unit)
            }
        })
        cont.invokeOnCancellation {
            try {
                socket.close(1000, "stopped")
            } catch (_: Throwable) {
            }
            socket.cancel()
        }
    }

    private fun parseTwitchLine(line: String): ChatMessage? {
        var rest = line
        val tags = HashMap<String, String>()
        if (rest.startsWith("@")) {
            val space = rest.indexOf(' ')
            if (space < 0) return null
            val tagPart = rest.substring(1, space)
            rest = rest.substring(space + 1)
            tagPart.split(';').forEach { entry ->
                val eq = entry.indexOf('=')
                if (eq > 0) {
                    tags[entry.substring(0, eq)] = unescapeIrcTag(entry.substring(eq + 1))
                }
            }
        }
        // :user!user@user.tmi.twitch.tv PRIVMSG #channel :message
        val privmsg = rest.indexOf(" PRIVMSG ")
        if (privmsg < 0) return null
        val prefix = rest.removePrefix(":")
        val userFromPrefix = prefix.substringBefore('!').takeIf { it.isNotBlank() }
        val after = rest.substring(privmsg + " PRIVMSG ".length)
        val colon = after.indexOf(" :")
        if (colon < 0) return null
        val text = after.substring(colon + 2).trim()
        if (text.isBlank()) return null
        val author = tags["display-name"]?.takeIf { it.isNotBlank() }
            ?: userFromPrefix
            ?: "viewer"
        val color = parseColor(tags["color"]) ?: colorFor(author)
        val timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val id = tags["id"] ?: UUID.randomUUID().toString()
        return ChatMessage(
            id = "twitch:$id",
            author = author,
            text = sanitize(text),
            colorArgb = color,
            platform = ChatPlatform.TWITCH,
            timestampMs = timestamp,
        )
    }

    private fun unescapeIrcTag(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    's' -> out.append(' ')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    ':' -> out.append(';')
                    '\\' -> out.append('\\')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    // -----------------------------------------------------------------------
    // Generic JSON WebSocket
    // -----------------------------------------------------------------------

    private suspend fun customLoop(config: ChatSettings) {
        var backoff = 2_000L
        while (running && config == settings && config.hasCustom) {
            try {
                connectCustom(config)
                backoff = 2_000L
            } catch (_: Throwable) {
            }
            if (!running || config != settings) break
            setStatus("custom", "reconnecting…")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
        setStatus("custom", "offline")
    }

    private suspend fun connectCustom(config: ChatSettings) = suspendCancellableCoroutine<Unit> { cont ->
        val url = config.customWebSocketUrl.trim()
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            setStatus("custom", "invalid URL")
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val request = Request.Builder().url(url).build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                setStatus("custom", "connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val element = try {
                    json.parseToJsonElement(text)
                } catch (_: Throwable) {
                    if (text.isNotBlank()) {
                        onMessage(
                            ChatMessage(
                                id = "custom:${UUID.randomUUID()}",
                                author = "chat",
                                text = sanitize(text.trim()),
                                colorArgb = colorFor("chat"),
                                platform = ChatPlatform.CUSTOM,
                                timestampMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                    return
                }
                val obj = element as? JsonObject ?: return
                val author = resolvePath(obj, config.customUserField)?.primitiveString()?.takeIf { it.isNotBlank() } ?: "chat"
                val message = resolvePath(obj, config.customMessageField)?.primitiveString() ?: return
                if (message.isBlank()) return
                val color = parseColor(resolvePath(obj, config.customColorField)?.primitiveString()) ?: colorFor(author)
                onMessage(
                    ChatMessage(
                        id = "custom:${UUID.randomUUID()}",
                        author = author,
                        text = sanitize(message),
                        colorArgb = color,
                        platform = ChatPlatform.CUSTOM,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                setStatus("custom", "error: ${t.message ?: "connection failed"}")
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (cont.isActive) cont.resume(Unit)
            }
        })
        cont.invokeOnCancellation {
            try {
                socket.close(1000, "stopped")
            } catch (_: Throwable) {
            }
            socket.cancel()
        }
    }

    private fun resolvePath(root: JsonObject, path: String): JsonElement? {
        if (path.isBlank()) return null
        var current: JsonElement = root
        for (part in path.split('.')) {
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return current
    }

    private fun JsonElement.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull

    // -----------------------------------------------------------------------
    // Demo
    // -----------------------------------------------------------------------

    private suspend fun demoLoop() {
        setStatus("demo", "demo chat playing")
        var index = 0
        while (running && settings.demoMessages) {
            delay(3_400)
            val (author, text) = demoLines[index % demoLines.size]
            index++
            onMessage(
                ChatMessage(
                    id = "demo:${UUID.randomUUID()}",
                    author = author,
                    text = text,
                    colorArgb = colorFor(author),
                    platform = ChatPlatform.DEMO,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
        }
        setStatus("demo", "offline")
    }

    // -----------------------------------------------------------------------

    private fun setStatus(source: String, value: String) {
        synchronized(statuses) {
            statuses[source] = value
        }
        publishStatus()
    }

    private fun publishStatus() {
        val snapshot = synchronized(statuses) { statuses.toMap() }
        val text = when {
            snapshot.isEmpty() -> "Chat offline"
            snapshot.size == 1 -> {
                val (source, value) = snapshot.entries.first()
                "${source.replaceFirstChar { it.uppercase() }}: $value"
            }
            else -> snapshot.entries.joinToString(" · ") { (source, value) ->
                "${source.replaceFirstChar { it.uppercase() }}: $value"
            }
        }
        onStatus(text)
    }

    private fun parseColor(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().removePrefix("#").removePrefix("0x")
        return try {
            val value = cleaned.toLong(16)
            if (cleaned.length <= 6) (0xFF000000L or value) else value
        } catch (_: Throwable) {
            null
        }
    }

    private fun colorFor(author: String): Long {
        val palette = longArrayOf(
            0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFD93D, 0xFFA78BFA,
            0xFF60A5FA, 0xFFF472B6, 0xFF34D399, 0xFFFB923C,
        )
        return palette[abs(author.hashCode()) % palette.size]
    }

    private fun sanitize(text: String): String =
        text.replace("\r", " ").replace("\u0000", "").take(500)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val demoLines = listOf(
            "pixelwander" to "yo the overlay looks clean",
            "nightshift" to "how's the connection out there?",
            "koda" to "that camera angle is perfect",
            "mirage" to "greetings from the other side of the world",
            "boots" to "let's gooo",
            "sable" to "chat is moving fast today",
        )
    }
}
