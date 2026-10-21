package uk.veritaslabs.vesta.location

/** Sequential reader over the pack wire formats: LEB128 varints, zigzag deltas, NUL-terminated UTF-8. */
internal class ByteReader(private val bytes: ByteArray, private var pos: Int = 0) {

    fun u8(): Int {
        require(pos < bytes.size) { "Unexpected end of data" }
        return bytes[pos++].toInt() and 0xFF
    }

    fun varint(): Int {
        var result = 0L
        var shift = 0
        while (true) {
            val b = u8()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            require(shift <= 35) { "Varint too long" }
        }
        require(result <= Int.MAX_VALUE) { "Varint out of range" }
        return result.toInt()
    }

    fun zigzag(): Int {
        val z = varint()
        return if (z and 1 == 0) z shr 1 else -((z + 1) shr 1)
    }

    fun take(count: Int): ByteArray {
        require(count >= 0 && pos + count <= bytes.size) { "Unexpected end of data" }
        return bytes.copyOfRange(pos, pos + count).also { pos += count }
    }

    fun ascii(count: Int): String = take(count).decodeToString()

    fun cString(): String {
        val start = pos
        while (u8() != 0) { /* scan to NUL */ }
        return bytes.decodeToString(start, pos - 1)
    }
}
