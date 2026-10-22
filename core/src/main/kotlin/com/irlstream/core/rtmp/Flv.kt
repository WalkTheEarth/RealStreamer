package com.irlstream.core.rtmp

import java.io.ByteArrayOutputStream

/**
 * FLV tag bodies for RTMP audio/video messages, plus H.264 helpers.
 */
object Flv {
    private const val AUDIO_TAG_AAC = 0xAF // AAC, 44 kHz, 16-bit, stereo (AAC's own ASC carries the real rate)
    private const val VIDEO_CODEC_AVC = 0x07
    private const val FRAME_KEY = 0x10
    private const val FRAME_INTER = 0x20

    fun videoSequenceHeader(avcDecoderConfigRecord: ByteArray): ByteArray =
        byteArrayOf((FRAME_KEY or VIDEO_CODEC_AVC).toByte(), 0, 0, 0, 0) + avcDecoderConfigRecord

    fun audioSequenceHeader(audioSpecificConfig: ByteArray): ByteArray =
        byteArrayOf(AUDIO_TAG_AAC.toByte(), 0) + audioSpecificConfig

    fun videoFrame(avccAccessUnit: ByteArray, keyframe: Boolean): ByteArray {
        val header = if (keyframe) FRAME_KEY else FRAME_INTER
        return byteArrayOf((header or VIDEO_CODEC_AVC).toByte(), 1, 0, 0, 0) + avccAccessUnit
    }

    fun audioFrame(rawAac: ByteArray): ByteArray =
        byteArrayOf(AUDIO_TAG_AAC.toByte(), 1) + rawAac
}

object H264 {

    private const val NAL_SPS = 7
    private const val NAL_PPS = 8

    /**
     * Converts an Annex-B access unit (start-code framed) to AVCC (4-byte big-endian
     * length prefixed). Already-AVCC input is returned unchanged.
     */
    fun annexBToAvcc(data: ByteArray): ByteArray {
        if (data.isEmpty() || !hasStartCode(data)) return data
        val out = ByteArrayOutputStream(data.size + 16)
        var i = 0
        val nals = ArrayList<ByteArray>()
        while (i < data.size) {
            val start = findStartCode(data, i)
            if (start < 0) break
            val nalStart = start + startCodeLength(data, start)
            val next = findStartCode(data, nalStart)
            val nalEnd = if (next < 0) data.size else next
            if (nalEnd > nalStart) {
                nals += data.copyOfRange(nalStart, nalEnd)
            }
            i = if (next < 0) data.size else next
        }
        for (nal in nals) {
            if (nal.isEmpty()) continue
            out.write((nal.size ushr 24) and 0xFF)
            out.write((nal.size ushr 16) and 0xFF)
            out.write((nal.size ushr 8) and 0xFF)
            out.write(nal.size and 0xFF)
            out.write(nal)
        }
        return out.toByteArray()
    }

    /** Extracts SPS/PPS NAL units from codec config data (usually `csd-0` and `csd-1`). */
    fun extractParameterSets(vararg csd: ByteArray?): Pair<ByteArray, ByteArray>? {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        csd.filterNotNull().forEach { buffer ->
            val nals = splitAnnexB(buffer)
            nals.forEach { nal ->
                when (nal.firstOrNull()?.toInt()?.and(0x1F)) {
                    NAL_SPS -> if (sps == null) sps = nal
                    NAL_PPS -> if (pps == null) pps = nal
                }
            }
        }
        val s = sps ?: return null
        val p = pps ?: return null
        return s to p
    }

    /** Builds an AVCDecoderConfigurationRecord from SPS and PPS. */
    fun buildAvcC(sps: ByteArray, pps: ByteArray): ByteArray {
        require(sps.size >= 4) { "SPS too short" }
        val out = ByteArrayOutputStream(64)
        out.write(1) // configurationVersion
        out.write(sps[1].toInt() and 0xFF) // AVCProfileIndication
        out.write(sps[2].toInt() and 0xFF) // profile_compatibility
        out.write(sps[3].toInt() and 0xFF) // AVCLevelIndication
        out.write(0xFF) // 6 bits reserved + lengthSizeMinusOne = 3
        out.write(0xE1) // 3 bits reserved + numOfSequenceParameterSets = 1
        out.write((sps.size ushr 8) and 0xFF)
        out.write(sps.size and 0xFF)
        out.write(sps)
        out.write(1) // numOfPictureParameterSets
        out.write((pps.size ushr 8) and 0xFF)
        out.write(pps.size and 0xFF)
        out.write(pps)
        return out.toByteArray()
    }

    /** Builds an AVCDecoderConfigurationRecord from MediaCodec csd buffers. */
    fun avcCFromCsd(vararg csd: ByteArray?): ByteArray? {
        val (sps, pps) = extractParameterSets(*csd) ?: return null
        return buildAvcC(sps, pps)
    }

    fun splitAnnexB(data: ByteArray): List<ByteArray> {
        val nals = ArrayList<ByteArray>()
        var i = 0
        while (i < data.size) {
            val start = findStartCode(data, i)
            if (start < 0) break
            val nalStart = start + startCodeLength(data, start)
            val next = findStartCode(data, nalStart)
            val end = if (next < 0) data.size else next
            if (end > nalStart) nals += data.copyOfRange(nalStart, end)
            i = if (next < 0) data.size else next
        }
        return nals
    }

    fun hasStartCode(data: ByteArray): Boolean = findStartCode(data, 0) >= 0

    private fun findStartCode(data: ByteArray, from: Int): Int {
        var i = from
        while (i + 3 <= data.size) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) return i
                if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) return i
            }
            i++
        }
        return -1
    }

    private fun startCodeLength(data: ByteArray, at: Int): Int =
        if (at + 3 < data.size && data[at + 2] == 0.toByte()) 4 else 3
}

/**
 * AudioSpecificConfig normalization. MediaCodec may hand back a 2-byte ASC;
 * some encoders return 5-byte ADTS-ish headers. RTMP needs the 2-byte ASC.
 */
object Aac {
    fun normalizeAsc(csd: ByteArray): ByteArray =
        if (csd.size >= 2) csd.copyOfRange(0, 2) else csd

    fun stripAdts(data: ByteArray): ByteArray {
        if (data.size >= 7 && data[0] == 0xFF.toByte() && (data[1].toInt() and 0xF0) == 0xF0) {
            val headerLen = if ((data[1].toInt() and 0x01) == 0) 9 else 7
            if (data.size > headerLen) return data.copyOfRange(headerLen, data.size)
        }
        return data
    }
}
