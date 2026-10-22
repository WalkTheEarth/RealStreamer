package com.irlstream.app.engine

import com.irlstream.core.model.Destination
import com.irlstream.core.rtmp.RtmpPublisher
import com.irlstream.core.rtmp.RtmpUrl
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class EncodedVideoFrame(val data: ByteArray, val ptsMs: Long, val keyframe: Boolean)

data class EncodedAudioFrame(val data: ByteArray, val ptsMs: Long)

/**
 * Fans one encoded stream out to N RTMP destinations. Every destination has its own
 * connection thread and bounded queue, so one slow service never stalls another.
 */
class StreamPipeline(
    private val onDestinationState: (DestinationRuntime) -> Unit,
    private val onRequestKeyframe: () -> Unit,
) {
    interface ConfigProvider {
        val videoConfig: ByteArray?
        val audioConfig: ByteArray?
    }

    private val workers = ConcurrentHashMap<String, DestinationWorker>()
    private val config = object : ConfigProvider {
        @Volatile
        override var videoConfig: ByteArray? = null

        @Volatile
        override var audioConfig: ByteArray? = null
    }

    @Volatile
    var running = false
        private set

    fun start(destinations: List<Destination>) {
        running = true
        updateDestinations(destinations)
    }

    fun setVideoConfig(avcC: ByteArray) {
        config.videoConfig = avcC
    }

    fun setAudioConfig(asc: ByteArray) {
        config.audioConfig = asc
    }

    fun updateDestinations(destinations: List<Destination>) {
        val wanted = destinations.filter { it.enabled }.associateBy { it.id }
        workers.keys.filter { it !in wanted.keys }.forEach { id ->
            workers.remove(id)?.stop()
            onDestinationState(
                DestinationRuntime(
                    id = id,
                    name = "",
                    maskedUrl = "",
                    status = DestinationStatus.OFFLINE,
                ),
            )
        }
        wanted.values.forEach { destination ->
            val existing = workers[destination.id]
            if (existing != null && existing.matches(destination) && existing.isRunning) {
                existing.update(destination)
            } else {
                existing?.stop()
                workers.remove(destination.id)
                val worker = DestinationWorker(
                    destination = destination,
                    configProvider = config,
                    onState = onDestinationState,
                    requestKeyframe = onRequestKeyframe,
                )
                workers[destination.id] = worker
                worker.start()
            }
        }
    }

    fun dispatchVideo(frame: EncodedVideoFrame) {
        if (!running) return
        workers.values.forEach { it.enqueueVideo(frame) }
    }

    fun dispatchAudio(frame: EncodedAudioFrame) {
        if (!running) return
        workers.values.forEach { it.enqueueAudio(frame) }
    }

    fun totalDropped(): Long = workers.values.sumOf { it.droppedFrames() }

    fun stop() {
        running = false
        workers.values.forEach { it.stop() }
        workers.clear()
    }
}

internal class DestinationWorker(
    @Volatile private var destination: Destination,
    private val configProvider: StreamPipeline.ConfigProvider,
    private val onState: (DestinationRuntime) -> Unit,
    private val requestKeyframe: () -> Unit,
) {
    private sealed interface Packet {
        data class Video(val frame: EncodedVideoFrame) : Packet
        data class Audio(val frame: EncodedAudioFrame) : Packet
        data object Stop : Packet
    }

    private val queue = ArrayBlockingQueue<Packet>(512)
    private val drops = AtomicLong()
    @Volatile private var running = false
    @Volatile private var publisher: RtmpPublisher? = null
    private var thread: Thread? = null
    private var watchdog: Thread? = null

    val isRunning: Boolean get() = running

    fun matches(other: Destination): Boolean =
        other.url == destination.url && other.preset == destination.preset

    fun update(other: Destination) {
        destination = other
    }

    fun start() {
        if (running) return
        running = true
        val thread = Thread({ run() }, "rtmp-${destination.id.take(6)}")
        thread.isDaemon = true
        this.thread = thread
        thread.start()
    }

    fun stop() {
        running = false
        try {
            publisher?.close()
        } catch (_: Throwable) {
        }
        queue.clear()
        queue.offer(Packet.Stop)
        try {
            thread?.join(1200)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        watchdog = null
    }

    fun droppedFrames(): Long = drops.get()

    fun enqueueVideo(frame: EncodedVideoFrame) {
        if (!running) return
        val size = queue.size
        if (size > 380 && !frame.keyframe) {
            drops.incrementAndGet()
            return
        }
        if (!queue.offer(Packet.Video(frame))) {
            drops.incrementAndGet()
        }
    }

    fun enqueueAudio(frame: EncodedAudioFrame) {
        if (!running) return
        if (queue.size > 420) {
            drops.incrementAndGet()
            return
        }
        if (!queue.offer(Packet.Audio(frame))) {
            drops.incrementAndGet()
        }
    }

    private fun run() {
        var attempt = 0
        while (running) {
            val dest = destination
            if (!dest.enabled) {
                state(DestinationStatus.DISABLED)
                sleepWhileRunning(500)
                continue
            }
            val url = RtmpUrl.parse(dest.url)
            if (url == null) {
                state(DestinationStatus.ERROR, "Invalid RTMP URL")
                sleepWhileRunning(2_000)
                continue
            }

            val videoConfig = waitForVideoConfig()
            if (videoConfig == null) {
                state(DestinationStatus.ERROR, "Encoder not ready")
                sleepWhileRunning(1_000)
                continue
            }
            queue.clear()
            state(if (attempt == 0) DestinationStatus.CONNECTING else DestinationStatus.RECONNECTING)

            var pub: RtmpPublisher? = null
            var liveSince = 0L
            try {
                pub = RtmpPublisher(url)
                publisher = pub
                pub.connectAndPublish()
                pub.sendVideoSequenceHeader(videoConfig)
                var audioSeqSent = false
                configProvider.audioConfig?.let {
                    pub.sendAudioSequenceHeader(it)
                    audioSeqSent = true
                }
                requestKeyframe()
                state(DestinationStatus.LIVE)
                liveSince = System.currentTimeMillis()
                startWatchdog(pub)
                attempt = 0

                var awaitingKeyframe = true
                var timestampBaseMs: Long? = null
                var lastStats = System.currentTimeMillis()
                var lastBytes = pub.bytesSent.get()

                while (running && destination.enabled && pub.isPublishing) {
                    val packet = try {
                        queue.poll(500, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        null
                    }
                    if (packet != null) {
                        when (packet) {
                            is Packet.Stop -> break
                            is Packet.Video -> {
                                if (awaitingKeyframe) {
                                    if (!packet.frame.keyframe) continue
                                    awaitingKeyframe = false
                                    // Each RTMP connection is a new stream. Rebase timestamps
                                    // so reconnects restart near zero instead of hours in.
                                    timestampBaseMs = packet.frame.ptsMs
                                }
                                val pts = (packet.frame.ptsMs - (timestampBaseMs ?: packet.frame.ptsMs)).coerceAtLeast(0)
                                pub.sendVideo(packet.frame.data, pts, packet.frame.keyframe)
                            }
                            is Packet.Audio -> {
                                if (awaitingKeyframe) continue
                                if (!audioSeqSent) {
                                    val asc = configProvider.audioConfig ?: continue
                                    pub.sendAudioSequenceHeader(asc)
                                    audioSeqSent = true
                                }
                                val pts = (packet.frame.ptsMs - (timestampBaseMs ?: packet.frame.ptsMs)).coerceAtLeast(0)
                                pub.sendAudio(packet.frame.data, pts)
                            }
                        }
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastStats >= 1_000) {
                        val seconds = (now - lastStats) / 1000.0
                        val bytes = pub.bytesSent.get()
                        val kbps = (((bytes - lastBytes) * 8) / 1000.0 / seconds).toInt().coerceAtLeast(0)
                        val dropped = drops.get()
                        state(
                            DestinationStatus.LIVE,
                            bitrateKbps = kbps,
                            dropped = dropped,
                            attempts = attempt,
                        )
                        lastStats = now
                        lastBytes = bytes
                    }
                }
                if (running && destination.enabled) {
                    throw IllegalStateException("Connection closed")
                }
            } catch (t: Throwable) {
                if (running && destination.enabled) {
                    val stable = liveSince > 0 && System.currentTimeMillis() - liveSince > 15_000
                    if (stable) attempt = 0
                    attempt++
                    val message = t.message ?: t.javaClass.simpleName
                    state(DestinationStatus.RECONNECTING, shorten(message), attempts = attempt)
                    if (!destination.autoReconnect) {
                        state(DestinationStatus.ERROR, shorten(message), attempts = attempt)
                        break
                    }
                }
            } finally {
                stopWatchdog()
                try {
                    pub?.close()
                } catch (_: Throwable) {
                }
                publisher = null
                queue.clear()
            }

            if (!running) break
            if (!destination.enabled) {
                state(DestinationStatus.DISABLED)
                continue
            }
            val backoff = (500L * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(15_000L)
            if (!sleepWhileRunning(backoff)) break
        }
        running = false
    }

    private fun waitForVideoConfig(): ByteArray? {
        val deadline = System.currentTimeMillis() + 20_000
        while (running && destination.enabled && System.currentTimeMillis() < deadline) {
            configProvider.videoConfig?.let { return it }
            if (!sleepWhileRunning(100)) return configProvider.videoConfig
        }
        return configProvider.videoConfig
    }

    private fun startWatchdog(pub: RtmpPublisher) {
        val thread = Thread({
            while (running && pub.isPublishing) {
                try {
                    Thread.sleep(1_500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (pub.isStalled(15_000)) {
                    try {
                        pub.close()
                    } catch (_: Throwable) {
                    }
                    return@Thread
                }
            }
        }, "rtmp-watchdog")
        thread.isDaemon = true
        watchdog = thread
        thread.start()
    }

    private fun stopWatchdog() {
        watchdog = null
    }

    private fun sleepWhileRunning(ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (running && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(minOf(200L, end - System.currentTimeMillis()).coerceAtLeast(1L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return running
    }

    private fun state(
        status: DestinationStatus,
        message: String? = null,
        bitrateKbps: Int = 0,
        dropped: Long = drops.get(),
        attempts: Int = 0,
    ) {
        onState(
            DestinationRuntime(
                id = destination.id,
                name = destination.displayName,
                maskedUrl = RtmpUrl.parse(destination.url)?.masked() ?: "—",
                status = status,
                message = message,
                bitrateKbps = bitrateKbps,
                reconnectAttempts = attempts,
                droppedFrames = dropped,
            ),
        )
    }

    private fun shorten(message: String): String =
        message.replace(Regex("\\s+"), " ").take(160)
}
