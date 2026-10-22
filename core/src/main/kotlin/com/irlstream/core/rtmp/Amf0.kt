package com.irlstream.core.rtmp

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * Minimal AMF0 encoder/decoder — enough for RTMP connect/publish control commands.
 */
sealed interface AmfValue {
    data class Num(val value: Double) : AmfValue
    data class Bool(val value: Boolean) : AmfValue
    data class Str(val value: String) : AmfValue
    data class Obj(val entries: Map<String, AmfValue>) : AmfValue
    data class EcmaArray(val entries: Map<String, AmfValue>) : AmfValue
    data class StrictArray(val items: List<AmfValue>) : AmfValue
    data object Null : AmfValue
    data object Undefined : AmfValue
    data class Unsupported(val marker: Int) : AmfValue

    companion object {
        fun obj(vararg pairs: Pair<String, AmfValue>): Obj = Obj(linkedMapOf(*pairs))
        fun str(value: String): Str = Str(value)
        fun num(value: Number): Num = Num(value.toDouble())
        fun bool(value: Boolean): Bool = Bool(value)
    }
}

object Amf0 {
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_UNDEFINED = 0x06
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09
    private const val TYPE_STRICT_ARRAY = 0x0A
    private const val TYPE_LONG_STRING = 0x0C

    fun encode(values: List<AmfValue>): ByteArray {
        val out = ByteArrayOutputStream(256)
        values.forEach { writeValue(out, it) }
        return out.toByteArray()
    }

    fun decode(data: ByteArray, start: Int = 0): List<AmfValue> {
        val reader = Reader(data, start)
        val out = ArrayList<AmfValue>()
        while (reader.hasRemaining()) {
            out += reader.readValue()
        }
        return out
    }

    fun decodeOne(data: ByteArray, start: Int = 0): Pair<AmfValue, Int> {
        val reader = Reader(data, start)
        val value = reader.readValue()
        return value to reader.position
    }

    private fun writeValue(out: ByteArrayOutputStream, value: AmfValue) {
        when (value) {
            is AmfValue.Num -> {
                out.write(TYPE_NUMBER)
                writeDouble(out, value.value)
            }
            is AmfValue.Bool -> {
                out.write(TYPE_BOOLEAN)
                out.write(if (value.value) 1 else 0)
            }
            is AmfValue.Str -> {
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                if (bytes.size <= 0xFFFF) {
                    out.write(TYPE_STRING)
                    writeU16(out, bytes.size)
                } else {
                    out.write(TYPE_LONG_STRING)
                    writeU32(out, bytes.size)
                }
                out.write(bytes)
            }
            is AmfValue.Obj -> {
                out.write(TYPE_OBJECT)
                writeObjectEntries(out, value.entries)
                writeU16(out, 0)
                out.write(TYPE_OBJECT_END)
            }
            is AmfValue.EcmaArray -> {
                out.write(TYPE_ECMA_ARRAY)
                writeU32(out, value.entries.size)
                writeObjectEntries(out, value.entries)
                writeU16(out, 0)
                out.write(TYPE_OBJECT_END)
            }
            is AmfValue.StrictArray -> {
                out.write(TYPE_STRICT_ARRAY)
                writeU32(out, value.items.size)
                value.items.forEach { writeValue(out, it) }
            }
            AmfValue.Null -> out.write(TYPE_NULL)
            AmfValue.Undefined -> out.write(TYPE_UNDEFINED)
            is AmfValue.Unsupported -> out.write(value.marker)
        }
    }

    private fun writeObjectEntries(out: ByteArrayOutputStream, entries: Map<String, AmfValue>) {
        entries.forEach { (key, v) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            writeU16(out, keyBytes.size)
            out.write(keyBytes)
            writeValue(out, v)
        }
    }

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeDouble(out: ByteArrayOutputStream, value: Double) {
        val bits = java.lang.Double.doubleToLongBits(value)
        // AMF0 numbers are big-endian IEEE-754 doubles.
        for (i in 7 downTo 0) {
            out.write(((bits ushr (i * 8)) and 0xFF).toInt())
        }
    }

    private class Reader(private val data: ByteArray, start: Int) {
        var position: Int = start
            private set

        fun hasRemaining(): Boolean = position < data.size

        fun readValue(): AmfValue {
            val marker = readU8()
            return when (marker) {
                TYPE_NUMBER -> AmfValue.Num(readDouble())
                TYPE_BOOLEAN -> AmfValue.Bool(readU8() != 0)
                TYPE_STRING -> AmfValue.Str(readString(readU16()))
                TYPE_LONG_STRING -> AmfValue.Str(readString(readU32()))
                TYPE_OBJECT -> AmfValue.Obj(readObjectEntries())
                TYPE_ECMA_ARRAY -> {
                    val count = readU32()
                    // count is advisory; entries are terminated by the object end marker
                    @Suppress("UNUSED_VARIABLE")
                    val ignored = count
                    AmfValue.EcmaArray(readObjectEntries())
                }
                TYPE_STRICT_ARRAY -> {
                    val count = readU32()
                    val items = ArrayList<AmfValue>(count)
                    repeat(count) { items += readValue() }
                    AmfValue.StrictArray(items)
                }
                TYPE_NULL -> AmfValue.Null
                TYPE_UNDEFINED -> AmfValue.Undefined
                else -> AmfValue.Unsupported(marker)
            }
        }

        private fun readObjectEntries(): Map<String, AmfValue> {
            val entries = LinkedHashMap<String, AmfValue>()
            while (true) {
                val keyLen = readU16()
                if (keyLen == 0) {
                    val end = readU8()
                    if (end == TYPE_OBJECT_END) break
                    // Not the end: a zero-length key with a value is technically invalid.
                    entries[""] = readValueAfterMarker(end)
                    continue
                }
                val key = readString(keyLen)
                entries[key] = readValue()
            }
            return entries
        }

        private fun readValueAfterMarker(marker: Int): AmfValue {
            // Re-dispatch on an already-consumed marker.
            return when (marker) {
                TYPE_NUMBER -> AmfValue.Num(readDouble())
                TYPE_BOOLEAN -> AmfValue.Bool(readU8() != 0)
                TYPE_STRING -> AmfValue.Str(readString(readU16()))
                else -> AmfValue.Unsupported(marker)
            }
        }

        private fun readString(length: Int): String {
            requireAvailable(length)
            val s = String(data, position, length, Charsets.UTF_8)
            position += length
            return s
        }

        private fun readU8(): Int {
            requireAvailable(1)
            return data[position++].toInt() and 0xFF
        }

        private fun readU16(): Int = (readU8() shl 8) or readU8()

        private fun readU32(): Int = (readU8() shl 24) or (readU8() shl 16) or (readU8() shl 8) or readU8()

        private fun readDouble(): Double {
            var bits = 0L
            repeat(8) { bits = (bits shl 8) or readU8().toLong() }
            return java.lang.Double.longBitsToDouble(bits)
        }

        private fun requireAvailable(n: Int) {
            if (position + n > data.size) {
                throw EOFException("AMF0 decode truncated at $position (needed $n more bytes)")
            }
        }
    }
}
