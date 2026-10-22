package com.irlstream.core.rtmp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Amf0Test {

    @Test
    fun roundTripsScalarsAndObjects() {
        val value = AmfValue.obj(
            "app" to AmfValue.Str("live"),
            "fpad" to AmfValue.Bool(false),
            "capabilities" to AmfValue.Num(15.0),
            "nested" to AmfValue.obj("code" to AmfValue.Str("NetStream.Publish.Start")),
            "nullable" to AmfValue.Null,
            "array" to AmfValue.StrictArray(listOf(AmfValue.Num(1.0), AmfValue.Str("two"))),
        )
        val encoded = Amf0.encode(listOf(AmfValue.Str("connect"), AmfValue.Num(1.0), value))
        val decoded = Amf0.decode(encoded)

        assertEquals(3, decoded.size)
        assertEquals("connect", (decoded[0] as AmfValue.Str).value)
        assertEquals(1.0, (decoded[1] as AmfValue.Num).value, 0.0)
        val obj = (decoded[2] as AmfValue.Obj).entries
        assertEquals("live", (obj["app"] as AmfValue.Str).value)
        assertEquals(false, (obj["fpad"] as AmfValue.Bool).value)
        assertEquals(15.0, (obj["capabilities"] as AmfValue.Num).value, 0.0)
        val nested = (obj["nested"] as AmfValue.Obj).entries
        assertEquals("NetStream.Publish.Start", (nested["code"] as AmfValue.Str).value)
        assertEquals(AmfValue.Null, obj["nullable"])
        val array = (obj["array"] as AmfValue.StrictArray).items
        assertEquals(2, array.size)
        assertEquals(1.0, (array[0] as AmfValue.Num).value, 0.0)
    }

    @Test
    fun encodesKnownBytes() {
        // E.g. AMF0 string "app" => 0x02 0x0003 'a' 'p' 'p'
        val encoded = Amf0.encode(listOf(AmfValue.Str("app")))
        assertArrayEquals(
            byteArrayOf(0x02, 0x00, 0x03, 'a'.code.toByte(), 'p'.code.toByte(), 'p'.code.toByte()),
            encoded,
        )
    }

    @Test
    fun decodesCommandPayload() {
        val payload = Amf0.encode(
            listOf(
                AmfValue.Str("onStatus"),
                AmfValue.Num(0.0),
                AmfValue.Null,
                AmfValue.obj(
                    "level" to AmfValue.Str("status"),
                    "code" to AmfValue.Str("NetStream.Publish.Start"),
                    "description" to AmfValue.Str("Start publishing"),
                ),
            ),
        )
        val decoded = Amf0.decode(payload)
        assertEquals("onStatus", (decoded[0] as AmfValue.Str).value)
        val info = (decoded[3] as AmfValue.Obj).entries
        assertTrue(info.containsKey("code"))
    }
}
