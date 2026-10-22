package com.irlstream.core.rtmp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class H264Test {

    private fun b(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun annexB(vararg nals: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        nals.forEach {
            out.write(b(0, 0, 0, 1))
            out.write(it)
        }
        return out.toByteArray()
    }

    @Test
    fun convertsAnnexBToAvcc() {
        val sps = b(0x67, 0x42, 0x00, 0x1E, 0x11)
        val pps = b(0x68, 0xCE, 0x3C, 0x80)
        val idr = b(0x65, 0x01, 0x02, 0x03)
        val avcc = H264.annexBToAvcc(annexB(sps, pps, idr))

        val expected = java.io.ByteArrayOutputStream().apply {
            listOf(sps, pps, idr).forEach {
                write(b(0, 0, 0, it.size))
                write(it)
            }
        }.toByteArray()
        assertArrayEquals(expected, avcc)
    }

    @Test
    fun leavesAvccAlone() {
        val avcc = b(0, 0, 0, 4, 0x65, 1, 2, 3)
        assertArrayEquals(avcc, H264.annexBToAvcc(avcc))
    }

    @Test
    fun extractsParameterSetsAndBuildsAvcC() {
        val sps = b(0x67, 0x42, 0x00, 0x1E, 0xAA)
        val pps = b(0x68, 0xCE, 0x3C, 0x80)
        val (foundSps, foundPps) = H264.extractParameterSets(annexB(sps, pps))!!
        assertArrayEquals(sps, foundSps)
        assertArrayEquals(pps, foundPps)

        val config = H264.buildAvcC(sps, pps)
        assertEquals(1, config[0].toInt())
        assertEquals(0x42, config[1].toInt() and 0xFF)
        assertEquals(0xFF, config[4].toInt() and 0xFF) // lengthSizeMinusOne=3
        assertEquals(0xE1, config[5].toInt() and 0xFF) // one SPS
        assertEquals(1, config[13].toInt() and 0xFF) // one PPS (after 5-byte SPS)
        assertNotNull(H264.avcCFromCsd(annexB(sps, pps)))
    }

    @Test
    fun flvTagsHaveCorrectHeaders() {
        val avcc = b(0, 0, 0, 1, 0x65)
        val key = Flv.videoFrame(avcc, keyframe = true)
        assertEquals(0x17, key[0].toInt() and 0xFF)
        assertEquals(1, key[1].toInt())
        assertEquals(5, key.size - 5) // payload is unchanged
        val inter = Flv.videoFrame(avcc, keyframe = false)
        assertEquals(0x27, inter[0].toInt() and 0xFF)

        val audio = Flv.audioFrame(b(1, 2, 3))
        assertEquals(0xAF, audio[0].toInt() and 0xFF)
        assertEquals(1, audio[1].toInt())
        assertEquals(5, audio.size)

        val asc = Flv.audioSequenceHeader(b(0x12, 0x10))
        assertEquals(0xAF, asc[0].toInt() and 0xFF)
        assertEquals(0, asc[1].toInt())
        assertEquals(4, asc.size)
    }

    @Test
    fun producesNoStartCodeForAlreadyAvcc() {
        val avcc = b(0, 0, 0, 2, 0x65, 1)
        assertFalse(H264.hasStartCode(avcc))
        assertTrue(H264.hasStartCode(annexB(b(0x65))))
    }
}
