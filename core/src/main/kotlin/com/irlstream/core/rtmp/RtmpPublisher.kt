package com.irlstream.core.rtmp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A small, dependency-free RTMP 1.0 publisher.
 *
 * One publisher owns one TCP connection and one published stream. The app runs one
 * publisher per destination so a single encode can fan out to many services, each
 * with independent reconnect and backpressure behavior.
 *
 * Supported: RTMP and RTMPS (TLS), AMF0 connect/publish, chunk size negotiation,
 * window acknowledgements, ping responses, H.264/AVC and AAC-LC FLV tagging.
 */
class RtmpPublisher(
    private val url: RtmpUrl,
    private val connectTimeoutMs: Int = 12_000,
    private val ioTimeoutMs: Int = 15_000,
) {
    enum class State { IDLE, CONNECTING, PUBLISHING, CLOSED, FAILED }

    @Volatile
    var state: State = State.IDLE
        private set

    /** Invoked from the reader thread if an established connection fails. */
    @Volatile
    var onFailure: ((Throwable) -> Unit)? = null

    private var socket: Socket? = null
    private var counting: CountingInputStream? = null
    private var chunkWriter: RtmpChunkWriter? = null
    private var chunkReader: RtmpChunkReader? = null
    private var readerThread: Thread? = null
    private val writeLock = Any()

    @Volatile
    private var closeRequested = false

    @Volatile
    private var streamId: Int = 0

    @Volatile
    private var windowAckSize: Int = 0

    private var lastAckBytes: Long = 0

    @Volatile
    private var lastWriteNanos: Long = 0

    val bytesSent = AtomicLong()
    val videoFramesSent = AtomicLong()
    val audioFramesSent = AtomicLong()

    @Volatile
    var connectedAtEpochMs: Long = 0
        private set

    val isPublishing: Boolean get() = state == State.PUBLISHING

    /** True when the connection has been publishing but no write has completed recently. */
    fun isStalled(thresholdMs: Long = 15_000): Boolean {
        val last = lastWriteNanos
        if (state != State.PUBLISHING || last == 0L) return false
        return (System.nanoTime() - last) / 1_000_000 > thresholdMs
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    fun connectAndPublish() {
        if (state == State.PUBLISHING) throw RtmpException("Already publishing")
        state = State.CONNECTING
        closeRequested = false
        try {
            val raw = Socket()
            raw.tcpNoDelay = true
            raw.keepAlive = true
            raw.soTimeout = ioTimeoutMs
            raw.connect(InetSocketAddress(url.host, url.effectivePort), connectTimeoutMs)
            val socket = if (url.secure) wrapTls(raw) else raw
            this.socket = socket

            val counting = CountingInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
            this.counting = counting
            val input = DataInputStream(counting)
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

            RtmpHandshake.handshake(input, output)

            val writer = RtmpChunkWriter(output)
            chunkWriter = writer
            val reader = RtmpChunkReader(input)
            chunkReader = reader

            writer.writeMessage(CSID_CONTROL, MSG_AMF0_COMMAND, 0, 0, connectCommand())
            writer.flush()
            awaitResult(reader, transactionId = 1.0)

            writer.setChunkSize(4096)

            writer.writeMessage(
                CSID_CONTROL, MSG_AMF0_COMMAND, 0, 0,
                RtmpCommands.command("releaseStream", 2.0, AmfValue.Null, AmfValue.Str(url.streamName)),
            )
            writer.writeMessage(
                CSID_CONTROL, MSG_AMF0_COMMAND, 0, 0,
                RtmpCommands.command("FCPublish", 3.0, AmfValue.Null, AmfValue.Str(url.streamName)),
            )
            writer.writeMessage(
                CSID_CONTROL, MSG_AMF0_COMMAND, 0, 0,
                RtmpCommands.command("createStream", 4.0, AmfValue.Null),
            )
            writer.flush()

            val createResult = awaitResult(reader, transactionId = 4.0)
            val id = createResult.drop(2)
                .filterIsInstance<AmfValue.Num>()
                .lastOrNull()
                ?.value
                ?.toInt()
                ?: throw RtmpException("Server did not return a stream id")
            streamId = id

            writer.writeMessage(
                CSID_STREAM, MSG_AMF0_COMMAND, id, 0,
                RtmpCommands.command(
                    "publish", 5.0, AmfValue.Null,
                    AmfValue.Str(url.streamName),
                    AmfValue.Str("live"),
                ),
            )
            writer.flush()

            awaitPublishStart(reader)

            socket.soTimeout = 0
            connectedAtEpochMs = System.currentTimeMillis()
            state = State.PUBLISHING
            startReaderThread(socket)
        } catch (t: Throwable) {
            state = State.FAILED
            closeInternal(joinReader = false)
            if (t is RtmpException) throw t
            throw RtmpException("Could not publish to ${url.masked()}: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    private fun wrapTls(raw: Socket): Socket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(raw, url.host, url.effectivePort, true) as SSLSocket
        ssl.useClientMode = true
        val params = ssl.sslParameters
        params.endpointIdentificationAlgorithm = "HTTPS"
        ssl.sslParameters = params
        ssl.startHandshake()
        return ssl
    }

    private fun startReaderThread(socket: Socket) {
        val reader = chunkReader ?: return
        val thread = Thread({
            while (!closeRequested) {
                try {
                    val message = reader.readMessage()
                    handleServerMessage(message)
                    maybeAcknowledge()
                } catch (_: SocketTimeoutException) {
                    // A read timeout on an idle publish is benign; keep waiting.
                } catch (e: IOException) {
                    if (!closeRequested) {
                        state = State.FAILED
                        closeInternal(joinReader = false)
                        onFailure?.invoke(RtmpException("Connection lost: ${e.message}", e))
                    }
                    break
                } catch (e: Throwable) {
                    if (!closeRequested) {
                        state = State.FAILED
                        closeInternal(joinReader = false)
                        onFailure?.invoke(RtmpException("RTMP reader failed: ${e.message}", e))
                    }
                    break
                }
            }
        }, "rtmp-reader-${url.host}")
        thread.isDaemon = true
        readerThread = thread
        thread.start()
    }

    private fun awaitResult(reader: RtmpChunkReader, transactionId: Double): List<AmfValue> {
        while (true) {
            val message = reader.readMessage()
            when (message.typeId) {
                MSG_AMF0_COMMAND, MSG_AMF0_DATA -> {
                    val values = Amf0.decode(message.payload)
                    val name = (values.firstOrNull() as? AmfValue.Str)?.value
                    val txn = (values.getOrNull(1) as? AmfValue.Num)?.value
                    if (name == "_result" && txn == transactionId) return values
                    if (name == "_error" && txn == transactionId) {
                        throw RtmpException("Server rejected 'connect' (${describeError(values)})")
                    }
                }
                else -> handleControl(message)
            }
        }
    }

    private fun awaitPublishStart(reader: RtmpChunkReader) {
        while (true) {
            val message = reader.readMessage()
            when (message.typeId) {
                MSG_AMF0_COMMAND, MSG_AMF0_DATA -> {
                    val values = Amf0.decode(message.payload)
                    val name = (values.firstOrNull() as? AmfValue.Str)?.value ?: continue
                    if (name == "onStatus") {
                        val info = values.filterIsInstance<AmfValue.Obj>().lastOrNull()?.entries
                        val code = (info?.get("code") as? AmfValue.Str)?.value
                        val level = (info?.get("level") as? AmfValue.Str)?.value
                        when {
                            code == "NetStream.Publish.Start" -> return
                            level == "error" || code?.contains("Bad") == true -> {
                                val description = (info?.get("description") as? AmfValue.Str)?.value
                                throw RtmpException("Server refused publish: ${code ?: "unknown"}${description?.let { " — $it" } ?: ""}")
                            }
                        }
                    } else if (name == "_error") {
                        throw RtmpException("Server rejected publish (${describeError(values)})")
                    }
                }
                else -> handleControl(message)
            }
        }
    }

    private fun describeError(values: List<AmfValue>): String {
        val info = values.filterIsInstance<AmfValue.Obj>().lastOrNull()?.entries
        val code = (info?.get("code") as? AmfValue.Str)?.value
        val description = (info?.get("description") as? AmfValue.Str)?.value
        return listOfNotNull(code, description).joinToString(" — ").ifBlank { "unknown error" }
    }

    private fun handleServerMessage(message: RtmpMessage) {
        when (message.typeId) {
            MSG_AMF0_COMMAND, MSG_AMF0_DATA -> {
                val values = try {
                    Amf0.decode(message.payload)
                } catch (_: Exception) {
                    return
                }
                val name = (values.firstOrNull() as? AmfValue.Str)?.value ?: return
                if (name == "onStatus") {
                    val info = values.filterIsInstance<AmfValue.Obj>().lastOrNull()?.entries
                    val level = (info?.get("level") as? AmfValue.Str)?.value
                    if (level == "error") {
                        val code = (info?.get("code") as? AmfValue.Str)?.value ?: "error"
                        val description = (info?.get("description") as? AmfValue.Str)?.value
                        val error = RtmpException("Server error: $code${description?.let { " — $it" } ?: ""}")
                        state = State.FAILED
                        closeInternal(joinReader = false)
                        onFailure?.invoke(error)
                    }
                }
            }
            else -> handleControl(message)
        }
    }

    private fun handleControl(message: RtmpMessage) {
        when (message.typeId) {
            MSG_SET_CHUNK_SIZE -> Unit // handled by the chunk reader
            MSG_WINDOW_ACK_SIZE -> {
                if (message.payload.size >= 4) windowAckSize = readIntBE(message.payload, 0)
            }
            MSG_SET_PEER_BANDWIDTH -> {
                if (message.payload.size >= 4) {
                    val size = readIntBE(message.payload, 0)
                    sendRaw(MSG_WINDOW_ACK_SIZE, CSID_CONTROL, 0, writeIntBE(size))
                }
            }
            MSG_USER_CONTROL -> {
                if (message.payload.size >= 6) {
                    val event = ((message.payload[0].toInt() and 0xFF) shl 8) or
                        (message.payload[1].toInt() and 0xFF)
                    if (event == 6) { // PingRequest
                        val payload = ByteArray(6)
                        payload[0] = 0
                        payload[1] = 7 // PingResponse
                        System.arraycopy(message.payload, 2, payload, 2, 4)
                        sendRaw(MSG_USER_CONTROL, CSID_CONTROL, 0, payload)
                    }
                }
            }
        }
    }

    private fun maybeAcknowledge() {
        val size = windowAckSize
        if (size <= 0) return
        val count = counting?.count ?: return
        if (count - lastAckBytes >= size / 2) {
            lastAckBytes = count
            val sequence = (count and 0xFFFFFFFFL).toInt()
            sendRaw(MSG_ACK, CSID_CONTROL, 0, writeIntBE(sequence))
        }
    }

    // -----------------------------------------------------------------------
    // Media
    // -----------------------------------------------------------------------

    fun sendVideoSequenceHeader(avcC: ByteArray) {
        sendMedia(MSG_VIDEO, 0, Flv.videoSequenceHeader(avcC))
    }

    fun sendAudioSequenceHeader(asc: ByteArray) {
        sendMedia(MSG_AUDIO, 0, Flv.audioSequenceHeader(Aac.normalizeAsc(asc)))
    }

    fun sendVideo(avccAccessUnit: ByteArray, ptsMs: Long, keyframe: Boolean) {
        sendMedia(MSG_VIDEO, ptsMs, Flv.videoFrame(avccAccessUnit, keyframe))
        videoFramesSent.incrementAndGet()
    }

    fun sendAudio(rawAac: ByteArray, ptsMs: Long) {
        sendMedia(MSG_AUDIO, ptsMs, Flv.audioFrame(Aac.stripAdts(rawAac)))
        audioFramesSent.incrementAndGet()
    }

    private fun sendMedia(typeId: Int, ptsMs: Long, payload: ByteArray) {
        if (state != State.PUBLISHING) throw RtmpException("Not publishing")
        val writer = chunkWriter ?: throw RtmpException("No chunk writer")
        val id = streamId
        synchronized(writeLock) {
            try {
                writer.writeMessage(CSID_STREAM, typeId, id, ptsMs, payload)
                writer.flush()
                bytesSent.addAndGet(payload.size.toLong() + 24)
                lastWriteNanos = System.nanoTime()
            } catch (e: IOException) {
                state = State.FAILED
                closeInternal(joinReader = false)
                throw RtmpException("Write failed: ${e.message}", e)
            }
        }
    }

    private fun sendRaw(typeId: Int, csid: Int, timestamp: Long, payload: ByteArray) {
        val writer = chunkWriter ?: return
        synchronized(writeLock) {
            try {
                writer.writeMessage(csid, typeId, 0, timestamp, payload)
                writer.flush()
            } catch (_: IOException) {
                // Reader thread will observe the failure.
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shutdown
    // -----------------------------------------------------------------------

    fun close() {
        state = State.CLOSED
        closeInternal(joinReader = true)
    }

    private fun closeInternal(joinReader: Boolean) {
        closeRequested = true
        val s = socket
        socket = null
        try {
            s?.close()
        } catch (_: IOException) {
        }
        val thread = readerThread
        readerThread = null
        if (joinReader && thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(800)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        chunkWriter = null
        chunkReader = null
        counting = null
    }

    private fun connectCommand(): ByteArray {
        val props = AmfValue.obj(
            "app" to AmfValue.Str(url.app),
            "type" to AmfValue.Str("nonprivate"),
            "flashVer" to AmfValue.Str("FMLE/3.0 (compatible; FMSc/1.0)"),
            "tcUrl" to AmfValue.Str(url.tcUrl),
            "fpad" to AmfValue.Bool(false),
            "capabilities" to AmfValue.Num(15.0),
            "audioCodecs" to AmfValue.Num(3191.0),
            "videoCodecs" to AmfValue.Num(252.0),
            "videoFunction" to AmfValue.Num(1.0),
            "objectEncoding" to AmfValue.Num(0.0),
        )
        return RtmpCommands.command("connect", 1.0, props)
    }

    companion object {
        const val MSG_SET_CHUNK_SIZE = 1
        const val MSG_ACK = 3
        const val MSG_USER_CONTROL = 4
        const val MSG_WINDOW_ACK_SIZE = 5
        const val MSG_SET_PEER_BANDWIDTH = 6
        const val MSG_AUDIO = 8
        const val MSG_VIDEO = 9
        const val MSG_AMF0_DATA = 18
        const val MSG_AMF0_COMMAND = 20

        private const val CSID_CONTROL = 3
        private const val CSID_STREAM = 8
    }
}

internal object RtmpCommands {
    fun command(name: String, transactionId: Double, vararg values: AmfValue): ByteArray =
        Amf0.encode(listOf(AmfValue.Str(name), AmfValue.Num(transactionId)) + values.toList())
}

internal data class RtmpMessage(
    val typeId: Int,
    val streamId: Int,
    val timestamp: Long,
    val payload: ByteArray,
)

internal class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var count: Long = 0
        private set

    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) count++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) count += n
        return n
    }

    override fun close() = delegate.close()
}

internal object RtmpHandshake {
    fun handshake(input: DataInputStream, output: OutputStream) {
        val c1 = ByteArray(1536)
        c1[0] = 0
        c1[1] = 0
        c1[2] = 0
        c1[3] = 0
        val random = ByteArray(1528)
        SecureRandom().nextBytes(random)
        System.arraycopy(random, 0, c1, 8, random.size)

        output.write(3) // C0
        output.write(c1) // C1
        output.flush()

        val s0 = input.readUnsignedByte()
        if (s0 != 3) throw RtmpException("Unsupported RTMP version $s0")
        val s1 = ByteArray(1536)
        val s2 = ByteArray(1536)
        input.readFully(s1)
        input.readFully(s2)

        output.write(s1) // C2 echoes S1
        output.flush()
    }
}

internal class RtmpChunkWriter(private val out: OutputStream) {
    var chunkSize: Int = 128
        private set

    fun writeMessage(csid: Int, typeId: Int, streamId: Int, timestampMs: Long, payload: ByteArray) {
        require(csid in 2..63) { "Unsupported chunk stream id $csid" }
        require(payload.size <= 0xFFFFFF) { "RTMP message too large: ${payload.size}" }
        val ts = timestampMs and 0xFFFFFFFFL
        val extended = ts >= 0xFFFFFFL
        val headerTs = if (extended) 0xFFFFFF else ts.toInt()

        writeBasicHeader(0, csid)
        writeU24(out, headerTs)
        writeU24(out, payload.size)
        out.write(typeId)
        writeU32LE(out, streamId)
        if (extended) writeU32BE(out, ts)

        var offset = 0
        val first = minOf(chunkSize, payload.size)
        out.write(payload, 0, first)
        offset += first
        while (offset < payload.size) {
            writeBasicHeader(3, csid)
            if (extended) writeU32BE(out, ts)
            val n = minOf(chunkSize, payload.size - offset)
            out.write(payload, offset, n)
            offset += n
        }
    }

    fun setChunkSize(size: Int) {
        if (size == chunkSize) return
        require(size in 1..0x7FFFFF)
        val payload = ByteArray(4)
        payload[0] = (size ushr 24).toByte()
        payload[1] = (size ushr 16).toByte()
        payload[2] = (size ushr 8).toByte()
        payload[3] = size.toByte()
        writeMessage(2, RtmpPublisher.MSG_SET_CHUNK_SIZE, 0, 0, payload)
        chunkSize = size
    }

    fun flush() = out.flush()

    private fun writeBasicHeader(fmt: Int, csid: Int) {
        out.write((fmt shl 6) or csid)
    }
}

internal class RtmpChunkReader(private val input: DataInputStream) {
    var chunkSize: Int = 128
        private set
    private val states = HashMap<Int, ChunkState>()

    private class ChunkState {
        var timestamp: Long = 0
        var delta: Long = 0
        var messageLength: Int = 0
        var typeId: Int = 0
        var streamId: Int = 0
        var extended: Boolean = false
        var buffer: ByteArray? = null
        var position: Int = 0
        var remaining: Int = 0
    }

    fun readMessage(): RtmpMessage {
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("RTMP connection closed by server")
            val fmt = (b ushr 6) and 0x03
            var csid = b and 0x3F
            if (csid == 0) {
                csid = 64 + input.readUnsignedByte()
            } else if (csid == 1) {
                val lo = input.readUnsignedByte()
                val hi = input.readUnsignedByte()
                csid = 64 + lo + (hi shl 8)
            }
            val st = states.getOrPut(csid) { ChunkState() }
            val inProgress = st.buffer != null && st.remaining > 0

            if (!inProgress) {
                when (fmt) {
                    0 -> {
                        val tsField = readU24(input)
                        val length = readU24(input)
                        val type = input.readUnsignedByte()
                        val sid = readU32LE(input)
                        val ext = tsField == 0xFFFFFF
                        val ts = if (ext) readU32BE(input) else tsField.toLong()
                        st.timestamp = ts
                        st.delta = 0
                        st.extended = ext
                        st.messageLength = length
                        st.typeId = type
                        st.streamId = sid
                    }
                    1 -> {
                        val deltaField = readU24(input)
                        val length = readU24(input)
                        val type = input.readUnsignedByte()
                        val ext = deltaField == 0xFFFFFF
                        val delta = if (ext) readU32BE(input) else deltaField.toLong()
                        st.delta = delta
                        st.timestamp += delta
                        st.extended = ext
                        st.messageLength = length
                        st.typeId = type
                    }
                    2 -> {
                        val deltaField = readU24(input)
                        val ext = deltaField == 0xFFFFFF
                        val delta = if (ext) readU32BE(input) else deltaField.toLong()
                        st.delta = delta
                        st.timestamp += delta
                        st.extended = ext
                    }
                    3 -> {
                        if (st.extended) readU32BE(input)
                        st.timestamp += st.delta
                    }
                }
                if (st.messageLength < 0) throw RtmpException("Invalid RTMP message length")
                st.buffer = ByteArray(st.messageLength)
                st.position = 0
                st.remaining = st.messageLength
            } else {
                if (fmt != 3) throw RtmpException("Malformed RTMP chunk stream (expected continuation)")
                if (st.extended) readU32BE(input)
            }

            val toRead = minOf(chunkSize, st.remaining)
            if (toRead > 0) {
                input.readFully(st.buffer!!, st.position, toRead)
                st.position += toRead
                st.remaining -= toRead
            }

            if (st.remaining == 0) {
                val message = RtmpMessage(
                    typeId = st.typeId,
                    streamId = st.streamId,
                    timestamp = st.timestamp,
                    payload = st.buffer ?: ByteArray(0),
                )
                st.buffer = null
                st.position = 0
                if (message.typeId == RtmpPublisher.MSG_SET_CHUNK_SIZE && message.payload.size >= 4) {
                    val newSize = readIntBE(message.payload, 0)
                    if (newSize in 1..0x7FFFFF) chunkSize = newSize
                }
                return message
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Byte helpers
// ---------------------------------------------------------------------------

internal fun readU24(input: DataInputStream): Int =
    (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()

internal fun readU32BE(input: DataInputStream): Long =
    (input.readUnsignedByte().toLong() shl 24) or
        (input.readUnsignedByte().toLong() shl 16) or
        (input.readUnsignedByte().toLong() shl 8) or
        input.readUnsignedByte().toLong()

internal fun readU32LE(input: DataInputStream): Int =
    input.readUnsignedByte() or
        (input.readUnsignedByte() shl 8) or
        (input.readUnsignedByte() shl 16) or
        (input.readUnsignedByte() shl 24)

internal fun readIntBE(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

internal fun writeIntBE(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

internal fun writeU24(out: OutputStream, value: Int) {
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

internal fun writeU32BE(out: OutputStream, value: Long) {
    out.write(((value ushr 24) and 0xFF).toInt())
    out.write(((value ushr 16) and 0xFF).toInt())
    out.write(((value ushr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
}

internal fun writeU32LE(out: OutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 24) and 0xFF)
}
