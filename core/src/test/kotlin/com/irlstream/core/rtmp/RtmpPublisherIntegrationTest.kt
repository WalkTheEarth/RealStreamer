package com.irlstream.core.rtmp

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * End-to-end proof that the hand-written RTMP publisher produces a stream a real
 * server accepts. Generates media with FFmpeg, publishes it over our client, and
 * validates the server's recording with ffprobe.
 *
 * Skipped automatically when FFmpeg is not installed.
 */
class RtmpPublisherIntegrationTest {

    private data class VideoFrame(val data: ByteArray, val ptsMs: Long, val key: Boolean)
    private data class AudioFrame(val data: ByteArray, val ptsMs: Long)
    private data class Media(
        val avcC: ByteArray,
        val asc: ByteArray,
        val video: List<VideoFrame>,
        val audio: List<AudioFrame>,
    )

    @Test
    fun publishesValidStreamToRealRtmpServer() {
        val ffmpeg = findExecutable("ffmpeg")
        val ffprobe = findExecutable("ffprobe")
        assumeTrue("ffmpeg not available", ffmpeg != null)
        assumeTrue("ffprobe not available", ffprobe != null)

        val dir = Files.createTempDirectory("irlstream-rtmp-it").toFile()
        try {
            val input = File(dir, "source.flv")
            val generated = run(
                ffmpeg!!, "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
                "-t", "2",
                "-c:v", "libx264", "-preset", "ultrafast", "-profile:v", "baseline",
                "-pix_fmt", "yuv420p", "-g", "30",
                "-c:a", "aac", "-b:a", "128k", "-ar", "48000", "-ac", "2",
                "-f", "flv", input.absolutePath,
            )
            assertTrue("failed to generate media: ${generated.output}", input.isFile && input.length() > 1_000)

            val media = parseFlv(input.readBytes())
            assertTrue("no AVC config parsed", media.avcC.isNotEmpty())
            assertTrue("no audio config parsed", media.asc.isNotEmpty())
            assertTrue("no video frames parsed", media.video.isNotEmpty())
            assertTrue("no audio frames parsed", media.audio.isNotEmpty())

            val port = freePort()
            val output = File(dir, "received.flv")
            val serverLog = File(dir, "server.log")
            val server = ProcessBuilder(
                ffmpeg, "-hide_banner", "-loglevel", "warning", "-listen", "1",
                "-i", "rtmp://127.0.0.1:$port/live/test",
                "-c", "copy", "-y", output.absolutePath,
            ).redirectErrorStream(true).redirectOutput(serverLog).start()

            try {
                Thread.sleep(1_200)
                assertTrue("RTMP listen server exited early: ${serverLog.readText()}", server.isAlive)

                val url = RtmpUrl.parse("rtmp://127.0.0.1:$port/live/test")!!
                val publisher = RtmpPublisher(url, connectTimeoutMs = 8_000, ioTimeoutMs = 8_000)
                publisher.connectAndPublish()
                publisher.sendVideoSequenceHeader(media.avcC)
                publisher.sendAudioSequenceHeader(media.asc)

                val events = buildList {
                    media.video.forEach { add(Triple(it.ptsMs, 0, it)) }
                    media.audio.forEach { add(Triple(it.ptsMs, 1, it)) }
                }.sortedWith(compareBy({ it.first }, { it.second }))

                var previousTs = 0L
                for ((ts, kind, frame) in events) {
                    val sleepMs = ((ts - previousTs) / 4).coerceIn(0, 100)
                    if (sleepMs > 0) Thread.sleep(sleepMs)
                    previousTs = ts
                    if (kind == 0) {
                        val v = frame as VideoFrame
                        publisher.sendVideo(v.data, v.ptsMs, v.key)
                    } else {
                        val a = frame as AudioFrame
                        publisher.sendAudio(a.data, a.ptsMs)
                    }
                }
                publisher.close()

                val finished = server.waitFor(25, TimeUnit.SECONDS)
                val log = serverLog.takeIf { it.exists() }?.readText().orEmpty()
                assertTrue("ffmpeg did not finalize the stream. Log:\n$log", finished)
                assertTrue("server recording missing. Log:\n$log", output.isFile && output.length() > 1_000)

                val streamTypes = run(ffprobe!!, "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", output.absolutePath).output
                assertTrue("no video stream in server output: $streamTypes", streamTypes.contains("video"))
                assertTrue("no audio stream in server output: $streamTypes", streamTypes.contains("audio"))

                val frameCount = run(
                    ffprobe, "-v", "error", "-count_frames", "-select_streams", "v:0",
                    "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", output.absolutePath,
                ).output.trim().toIntOrNull() ?: 0
                assertTrue("expected decoded video frames, got $frameCount", frameCount > 0)
                println("RTMP integration OK: $frameCount video frames, output ${output.length()} bytes")
            } finally {
                server.destroyForcibly()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // -----------------------------------------------------------------------

    private fun parseFlv(bytes: ByteArray): Media {
        require(bytes.size > 13 && bytes[0] == 'F'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'V'.code.toByte())
        val dataOffset = ((bytes[5].toInt() and 0xFF) shl 24) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            ((bytes[7].toInt() and 0xFF) shl 8) or
            (bytes[8].toInt() and 0xFF)
        var pos = dataOffset + 4 // skip PreviousTagSize0
        var avcC: ByteArray? = null
        var asc: ByteArray? = null
        val video = ArrayList<VideoFrame>()
        val audio = ArrayList<AudioFrame>()

        while (pos + 11 + 4 <= bytes.size) {
            val tagType = bytes[pos].toInt() and 0xFF
            val size = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            val tsLow = ((bytes[pos + 4].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                (bytes[pos + 6].toInt() and 0xFF)
            val tsExt = bytes[pos + 7].toInt() and 0xFF
            val ts = tsLow.toLong() or (tsExt.toLong() shl 24)
            val payloadStart = pos + 11
            val payloadEnd = payloadStart + size
            if (payloadEnd > bytes.size) break
            val payload = bytes.copyOfRange(payloadStart, payloadEnd)

            if (tagType == 9 && payload.size > 5 && (payload[0].toInt() and 0x0F) == 7) {
                when (payload[1].toInt() and 0xFF) {
                    0 -> avcC = payload.copyOfRange(5, payload.size)
                    1 -> video += VideoFrame(
                        data = payload.copyOfRange(5, payload.size),
                        ptsMs = ts,
                        key = (payload[0].toInt() and 0xF0) == 0x10,
                    )
                }
            } else if (tagType == 8 && payload.size > 2 && ((payload[0].toInt() and 0xFF) shr 4) == 10) {
                when (payload[1].toInt() and 0xFF) {
                    0 -> asc = payload.copyOfRange(2, payload.size)
                    1 -> audio += AudioFrame(payload.copyOfRange(2, payload.size), ts)
                }
            }
            pos = payloadEnd + 4
        }
        return Media(avcC ?: ByteArray(0), asc ?: ByteArray(0), video, audio)
    }

    private data class CommandResult(val exit: Int, val output: String)

    private fun run(vararg command: String): CommandResult {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        return CommandResult(exit, output)
    }

    private fun findExecutable(name: String): String? {
        val path = System.getenv("PATH").orEmpty()
        return path.split(File.pathSeparator)
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
