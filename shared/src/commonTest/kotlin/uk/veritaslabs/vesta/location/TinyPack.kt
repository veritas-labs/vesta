package uk.veritaslabs.vesta.location

import kotlin.math.roundToInt

/** Test-only encoder for the pack wire formats (scripts/gers-index/gers_index.py), plus a small hand-built country. */
object TinyPack {

    class Spec(val name: String, val subtype: String, val parent: Int?, val lat: Double, val lon: Double)

    /** The "compression" is the identity here; JvmInflaterTest covers real zlib. */
    val identity = Inflater { it }

    val subtypes = listOf("country", "region", "county", "locality")

    // Row numbers are referred to by index throughout the tests; keep the order.
    val specs = listOf(
        Spec("United Kingdom", "country", null, 54.0, -2.0), // 0
        Spec("England", "region", 0, 52.0, -1.5), // 1
        Spec("Isle of Wight", "county", 1, 50.67, -1.3), // 2
        Spec("Newport", "locality", 2, 50.70, -1.29), // 3
        Spec("Newport", "locality", 1, 51.58, -3.0), // 4
        Spec("Cowes", "locality", 2, 50.76, -1.30), // 5
        Spec("Köln", "locality", 1, 50.94, 6.96), // 6 island: no neighbours
        Spec("Carisbrooke", "locality", 3, 50.69, -1.32), // 7 inside Newport, and next to it
        Spec("Westport", "locality", 1, 53.8, -9.5), // 8
        Spec("Port Erin", "locality", 1, 54.08, -4.76), // 9
        Spec("Old Port", "locality", 1, 55.0, -3.0), // 10
        Spec("Alpha Town", "locality", 2, 50.60, -1.29), // 11 same spot as Beta Town
        Spec("Beta Town", "locality", 2, 50.60, -1.29), // 12
        Spec("Shide", "locality", 3, 50.69, -1.28), // 13 inside Newport, not a neighbour
    )

    val edges = listOf(3 to 5, 3 to 7, 3 to 11, 3 to 12, 5 to 11)

    fun id(row: Int): String = "00000000-0000-0000-0000-" + (row + 1).toString(16).padStart(12, '0')

    fun directory(): LocalityDirectory = LocalityDirectory(pack())

    fun pack(): LocalityPack = LocalityPack.parse(namesChunk(), adjacencyChunk(), identity)

    fun varint(value: Int, out: MutableList<Byte>) {
        var v = value
        while (v >= 0x80) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
    }

    fun zigzag(value: Int, out: MutableList<Byte>) = varint(if (value >= 0) value * 2 else -value * 2 - 1, out)

    fun namesChunk(magic: String = "VGN2"): ByteArray {
        val out = ArrayList<Byte>()
        out += magic.encodeToByteArray().toList()
        varint(subtypes.size, out)
        subtypes.forEach { out += it.encodeToByteArray().toList() + 0.toByte() }
        varint(specs.size, out)
        specs.indices.forEach { row ->
            val uuid = id(row).replace("-", "")
            uuid.chunked(2).forEach { out += it.toInt(16).toByte() }
        }
        specs.forEach { out += subtypes.indexOf(it.subtype).toByte() }
        specs.forEach { varint((it.parent ?: -1) + 1, out) }
        specs.forEach { out += it.name.encodeToByteArray().toList() + 0.toByte() }
        for (pick in listOf<(Spec) -> Double>({ it.lat }, { it.lon })) {
            var previous = 0
            specs.forEach {
                val micro = (pick(it) * 1_000_000).roundToInt()
                zigzag(micro - previous, out)
                previous = micro
            }
        }
        return out.toByteArray()
    }

    fun adjacencyChunk(): ByteArray {
        val out = ArrayList<Byte>()
        specs.indices.forEach { row ->
            val list = edges.mapNotNull { (a, b) -> if (a == row) b else if (b == row) a else null }.sorted()
            varint(list.size, out)
            var previous = row
            list.forEach {
                zigzag(it - previous, out)
                previous = it
            }
        }
        return out.toByteArray()
    }

    fun rootChunk(
        release: String = "2026-09-23.1",
        country: String = "GB",
        names: Byte = 0xAB.toByte(),
        adjacency: Byte = 0x01,
        shards: List<Int> = listOf(7, 9),
        magic: String = "VGI2",
    ): ByteArray {
        val out = ArrayList<Byte>()
        out += magic.encodeToByteArray().toList()
        out += 5.toByte()
        out += release.padEnd(16, ' ').encodeToByteArray().toList()
        out += country.encodeToByteArray().toList()
        repeat(32) { out += names }
        repeat(32) { out += adjacency }
        out += (shards.size and 0xFF).toByte()
        out += (shards.size shr 8).toByte()
        shards.forEach { key ->
            repeat(4) { out += (key shr (8 * it)).toByte() }
            repeat(32) { out += 0x55 }
        }
        return out.toByteArray()
    }
}
