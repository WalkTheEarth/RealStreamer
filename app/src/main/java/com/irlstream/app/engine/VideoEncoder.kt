package com.irlstream.app.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.irlstream.core.rtmp.H264
import java.nio.ByteBuffer

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrateKbps: Int,
    private val keyframeIntervalSec: Int,
    private val listener: Listener,
) {
    interface Listener {
        fun onConfig(avcC: ByteArray)
        fun onFrame(data: ByteArray, ptsMs: Long, keyframe: Boolean)
        fun onError(message: String)
    }

    @Volatile
    private var running = false
    @Volatile private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var drainThread: Thread? = null

    /**
     * Configures and starts the encoder, returning the surface the compositor must draw
     * into. Must be called from a thread with a Looper? No — MediaCodec does not require one.
     */
    fun start(): Surface {
        stop()
        val (newCodec, surface) = createCodec()
        codec = newCodec
        inputSurface = surface
        running = true
        val thread = Thread({ drainLoop(newCodec) }, "video-encoder")
        thread.isDaemon = true
        drainThread = thread
        thread.start()
        return surface
    }

    private fun createCodec(): Pair<MediaCodec, Surface> {
        val candidates = listOf(
            { buildFormat(highProfile = true, extra = true) },
            { buildFormat(highProfile = false, extra = true) },
            { buildFormat(highProfile = false, extra = false) },
        )
        var lastError: Throwable? = null
        for (build in candidates) {
            var codec: MediaCodec? = null
            try {
                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                val format = build()
                if (format.containsKey(MediaFormat.KEY_BITRATE_MODE)) {
                    val mode = format.getInteger(MediaFormat.KEY_BITRATE_MODE)
                    if (caps.encoderCapabilities?.isBitrateModeSupported(mode) != true) {
                        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                    }
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                // createInputSurface() is only legal in the Configured state. Starting
                // the codec first makes every hardware encoder reject it.
                val surface = codec.createInputSurface()
                codec.start()
                return codec to surface
            } catch (t: Throwable) {
                lastError = t
                try {
                    codec?.stop()
                } catch (_: Throwable) {
                }
                try {
                    codec?.release()
                } catch (_: Throwable) {
                }
            }
        }
        throw IllegalStateException("No usable H.264 encoder: ${lastError?.message}", lastError)
    }

    private fun buildFormat(highProfile: Boolean, extra: Boolean): MediaFormat {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSec)
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        if (highProfile) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
        }
        if (extra) {
            try {
                format.setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime
            } catch (_: Throwable) {
            }
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                } catch (_: Throwable) {
                }
            }
        }
        return format
    }

    private fun drainLoop(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var configEmitted = false
        try {
            while (running) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        val avcC = H264.avcCFromCsd(format.byteBuffer("csd-0"), format.byteBuffer("csd-1"))
                        if (avcC != null) {
                            configEmitted = true
                            listener.onConfig(avcC)
                        }
                    }
                    index >= 0 -> {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                val keyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                if (!configEmitted && keyframe) {
                                    // Some encoders omit csd-0/csd-1 and only put SPS/PPS
                                    // in the first keyframe. Derive the AVC config from it.
                                    val avcC = H264.avcCFromCsd(bytes)
                                    if (avcC != null) {
                                        configEmitted = true
                                        listener.onConfig(avcC)
                                    }
                                }
                                if (configEmitted) {
                                    val avcc = H264.annexBToAvcc(bytes)
                                    if (avcc.isNotEmpty()) {
                                        listener.onFrame(avcc, info.presentationTimeUs / 1000, keyframe)
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            running = false
                        }
                    }
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }
        } catch (t: Throwable) {
            if (running) {
                Log.e(TAG, "encoder drain failed", t)
                listener.onError("Video encoder failed: ${t.message}")
            }
            running = false
        }
    }

    fun requestKeyFrame() {
        val codec = codec ?: return
        try {
            codec.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        running = false
        try {
            drainThread?.join(700)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        drainThread = null
        val c = codec
        codec = null
        if (c != null) {
            try {
                c.stop()
            } catch (_: Throwable) {
            }
            try {
                c.release()
            } catch (_: Throwable) {
            }
        }
        try {
            inputSurface?.release()
        } catch (_: Throwable) {
        }
        inputSurface = null
    }

    private fun MediaFormat.byteBuffer(key: String): ByteArray? {
        val buffer = getByteBuffer(key) ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    companion object {
        private const val TAG = "VideoEncoder"
    }
}
