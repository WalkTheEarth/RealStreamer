package com.irlstream.app.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.irlstream.core.model.AudioInput
import com.irlstream.core.model.AudioSettings
import kotlin.math.log10
import kotlin.math.sqrt

class AudioEngine(
    private val context: Context,
    private val settings: AudioSettings,
    private val streamEpochNanos: Long,
    private val listener: Listener,
) {
    interface Listener {
        fun onConfig(asc: ByteArray)
        fun onFrame(rawAac: ByteArray, ptsUs: Long)
        fun onLevel(level: Float)
        fun onError(message: String)
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null
    @Volatile private var record: AudioRecord? = null
    @Volatile private var codec: MediaCodec? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    fun start() {
        stop()
        running = true
        val thread = Thread({ captureLoop() }, "audio-capture")
        thread.isDaemon = true
        this.thread = thread
        thread.start()
    }

    @SuppressLint("MissingPermission") // checked explicitly below and in StreamEngine.goLive
    private fun captureLoop() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError("Microphone permission is required")
            running = false
            return
        }
        var audioRecord: AudioRecord? = null
        var audioCodec: MediaCodec? = null
        try {
            val rate = settings.sampleRate
            val channels = settings.channels.coerceIn(1, 2)
            val channelMask = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val minBuffer = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("Audio input unavailable (code $minBuffer)")
            val bufferSize = maxOf(minBuffer * 2, rate / 10 * channels * 2)

            audioRecord = AudioRecord.Builder()
                .setAudioSource(settings.input.toSource())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Microphone could not be initialized")
            }
            record = audioRecord

            applyAudioEffects(audioRecord.audioSessionId)

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec.start()
            codec = audioCodec

            audioRecord.startRecording()
            val recordingStartedNanos = SystemClock.elapsedRealtimeNanos()
            val basePtsUs = if (streamEpochNanos > 0) {
                ((recordingStartedNanos - streamEpochNanos) / 1_000).coerceAtLeast(0)
            } else {
                0L
            }
            val samplesPerRead = (rate / 50 * channels).coerceAtLeast(256) // ~20 ms
            val pcm = ShortArray(samplesPerRead)
            val pcmBytes = ByteArray(samplesPerRead * 2)
            val info = MediaCodec.BufferInfo()
            var totalSamples = 0L
            var lastLevelAt = 0L
            var configSent = false

            while (running) {
                val read = audioRecord.read(pcm, 0, pcm.size)
                if (read < 0) {
                    listener.onError("Microphone read failed ($read)")
                    break
                }
                if (read == 0) continue

                val samples = read
                val ptsUs = basePtsUs + totalSamples * 1_000_000L / (rate.toLong() * channels)
                totalSamples += samples

                var sumSquares = 0.0
                for (i in 0 until samples) {
                    val v = pcm[i].toInt()
                    pcmBytes[i * 2] = (v and 0xFF).toByte()
                    pcmBytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    val d = v / 32768.0
                    sumSquares += d * d
                }
                val now = System.currentTimeMillis()
                if (now - lastLevelAt >= 80) {
                    lastLevelAt = now
                    val rms = sqrt(sumSquares / samples).coerceAtLeast(1e-7)
                    val db = 20 * log10(rms)
                    listener.onLevel(((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat())
                }

                val inIndex = audioCodec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val input = audioCodec.getInputBuffer(inIndex)
                    if (input != null) {
                        input.clear()
                        input.put(pcmBytes, 0, samples * 2)
                        audioCodec.queueInputBuffer(inIndex, 0, samples * 2, ptsUs, 0)
                    }
                }

                while (true) {
                    val outIndex = audioCodec.dequeueOutputBuffer(info, 0)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val csd = audioCodec.outputFormat.getByteBuffer("csd-0")?.let { buffer ->
                                ByteArray(buffer.remaining()).also { buffer.get(it) }
                            }
                            val asc = if (csd != null && csd.isNotEmpty()) {
                                csd.copyOfRange(0, minOf(2, csd.size))
                            } else {
                                synthesizeAsc(rate, channels)
                            }
                            configSent = true
                            listener.onConfig(asc)
                        }
                        outIndex >= 0 -> {
                            if (info.size > 0) {
                                val output = audioCodec.getOutputBuffer(outIndex)
                                if (output != null) {
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    val data = ByteArray(info.size)
                                    output.get(data)
                                    listener.onFrame(data, info.presentationTimeUs)
                                }
                            }
                            audioCodec.releaseOutputBuffer(outIndex, false)
                        }
                        else -> break
                    }
                }

                if (!configSent) {
                    // Some encoders do not emit a format change buffer until later;
                    // synthesize a valid AAC-LC AudioSpecificConfig as a fallback.
                    val asc = synthesizeAsc(rate, channels)
                    configSent = true
                    listener.onConfig(asc)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "audio engine failed", t)
            if (running) listener.onError("Audio failed: ${t.message}")
        } finally {
            running = false
            releaseEffects()
            try {
                audioRecord?.stop()
            } catch (_: Throwable) {
            }
            try {
                audioRecord?.release()
            } catch (_: Throwable) {
            }
            record = null
            try {
                audioCodec?.stop()
            } catch (_: Throwable) {
            }
            try {
                audioCodec?.release()
            } catch (_: Throwable) {
            }
            codec = null
        }
    }

    private fun applyAudioEffects(sessionId: Int) {
        if (sessionId == 0) return
        try {
            if (settings.echoCancellation && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Throwable) {
        }
        try {
            if (settings.noiseSuppression && NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Throwable) {
        }
        try {
            if (settings.autoGain && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseEffects() {
        try {
            aec?.release()
        } catch (_: Throwable) {
        }
        try {
            ns?.release()
        } catch (_: Throwable) {
        }
        try {
            agc?.release()
        } catch (_: Throwable) {
        }
        aec = null
        ns = null
        agc = null
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            thread?.join(900)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        releaseEffects()
    }

    private fun AudioInput.toSource(): Int = when (this) {
        AudioInput.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        AudioInput.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
        AudioInput.MIC -> MediaRecorder.AudioSource.MIC
        AudioInput.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    companion object {
        private const val TAG = "AudioEngine"

        fun synthesizeAsc(sampleRate: Int, channels: Int): ByteArray {
            val frequencyIndex = when (sampleRate) {
                96000 -> 0
                88200 -> 1
                64000 -> 2
                48000 -> 3
                44100 -> 4
                32000 -> 5
                24000 -> 6
                22050 -> 7
                16000 -> 8
                12000 -> 9
                11025 -> 10
                8000 -> 11
                else -> 3
            }
            // AudioSpecificConfig: audioObjectType=2 (AAC-LC), freq index, channel config.
            val value = (2 shl 11) or (frequencyIndex shl 7) or (channels.coerceIn(1, 2) shl 3)
            return byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
        }
    }
}
