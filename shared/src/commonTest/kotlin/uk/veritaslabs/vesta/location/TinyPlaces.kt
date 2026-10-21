package uk.veritaslabs.vesta.location

import kotlin.math.roundToInt

/** Test-only encoder for the places pack formats (scripts/gers-index/gers_index.py), with identity "compression". */
object TinyPlaces {

    class Spec(val id: String, val name: String, val lat: Double, val lon: Double, val confidence: Double, val category: Int)

    /** A placed unit: which chunk, inside or nearby, and its places. */
    class UnitSpec(val chunk: Int, val nearby: Boolean, val places: List<Spec>)

    val identity = TinyPack.identity

    val groups = listOf("food_and_drink", "services")
    val categories = listOf("cafe" to 0, "hair_salon" to 1, "pub" to 0)

    fun uuid(n: Int): String = "11111111-2222-3333-4444-" + n.toString(16).padStart(12, '0')

    fun place(n: Int, name: String = "Place $n", lat: Double = 50.7 + n / 1000.0, lon: Double = -1.3 - n / 1000.0, confidence: Double = 1.0, category: Int = 0) =
        Spec(uuid(n), name, lat, lon, confidence, category)

    val units = listOf(
        UnitSpec(0, false, listOf(place(1, "Café Nero"), place(2, category = 1, confidence = 0.5), place(3, lat = -33.5, lon = 151.2))), // row 0 inside
        UnitSpec(0, true, listOf(place(4, category = 2))), // row 0 nearby
        UnitSpec(1, false, listOf(place(5), place(6))), // row 1 inside
        UnitSpec(1, false, listOf(place(7))), // row 1 inside, second range
        UnitSpec(1, true, listOf(place(8, "Nearby Pub", category = 2))), // row 2 nearby only
        UnitSpec(0, true, listOf(place(9))), // row 2 nearby, same chunk as row 0
    )

    /** Which unit indexes belong to which names-table row; row 3 has no places. */
    val rowUnits = listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5), emptyList())

    class Built(val chunks: List<ByteArray>, val ranges: List<List<PlaceRange>>)

    fun build(units: List<UnitSpec> = TinyPlaces.units, rowUnits: List<List<Int>> = TinyPlaces.rowUnits): Built {
        val chunkCount = (units.maxOfOrNull { it.chunk } ?: -1) + 1
        val chunks = List(chunkCount) { ArrayList<Byte>().apply { addAll("VGD1".encodeToByteArray().toList()) } }
        val where = units.map { unit ->
            val bytes = unit(unit.places)
            val chunk = chunks[unit.chunk]
            val offset = chunk.size
            chunk.addAll(bytes.toList())
            PlaceRange(unit.chunk, offset, bytes.size, unit.places.size, unit.nearby)
        }
        return Built(chunks.map { it.toByteArray() }, rowUnits.map { row -> row.map { where[it] } })
    }

    fun unit(places: List<Spec>): ByteArray {
        val out = ArrayList<Byte>()
        TinyPack.varint(places.size, out)
        places.forEach { p -> p.id.replace("-", "").chunked(2).forEach { out += it.toInt(16).toByte() } }
        for (pick in listOf<(Spec) -> Double>({ it.lat }, { it.lon })) {
            var previous = 0
            places.forEach {
                val micro = (pick(it) * 1_000_000).roundToInt()
                TinyPack.zigzag(micro - previous, out)
                previous = micro
            }
        }
        places.forEach { TinyPack.varint(it.category, out) }
        places.forEach { out += (it.confidence * 255).roundToInt().toByte() }
        places.forEach { out += it.name.encodeToByteArray().toList() + 0.toByte() }
        return out.toByteArray()
    }

    fun directory(
        chunkSizes: List<Int> = listOf(100, 250),
        ranges: List<List<PlaceRange>> = build().ranges,
        magic: String = "VGP2",
        rowCount: Int = ranges.size,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out += magic.encodeToByteArray().toList()
        TinyPack.varint(chunkSizes.size, out)
        chunkSizes.forEach { TinyPack.varint(it, out) }
        TinyPack.varint(rowCount, out)
        ranges.forEach { row ->
            TinyPack.varint(row.size, out)
            row.forEach {
                TinyPack.varint(it.chunk, out)
                TinyPack.varint(it.offset, out)
                TinyPack.varint(it.length, out)
                TinyPack.varint(it.count shl 1 or (if (it.nearby) 1 else 0), out)
            }
        }
        return out.toByteArray()
    }

    fun categoryTable(magic: String = "VGC1", groups: List<String> = TinyPlaces.groups, cats: List<Pair<String, Int>> = categories): ByteArray {
        val out = ArrayList<Byte>()
        out += magic.encodeToByteArray().toList()
        TinyPack.varint(groups.size, out)
        groups.forEach { out += it.encodeToByteArray().toList() + 0.toByte() }
        TinyPack.varint(cats.size, out)
        cats.forEach { out += it.first.encodeToByteArray().toList() + 0.toByte() }
        cats.forEach { TinyPack.varint(it.second, out) }
        return out.toByteArray()
    }

    fun rootChunk(
        release: String = "2026-09-23.1",
        country: String = "GB",
        directory: Byte = 0xD1.toByte(),
        categories: Byte = 0xC1.toByte(),
        chunks: List<Byte> = listOf(1, 2),
        magic: String = "VGP1",
        declaredCount: Int = chunks.size,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out += magic.encodeToByteArray().toList()
        out += release.padEnd(16, ' ').encodeToByteArray().toList()
        out += country.encodeToByteArray().toList()
        repeat(32) { out += directory }
        repeat(32) { out += categories }
        repeat(4) { out += (declaredCount shr (8 * it)).toByte() }
        chunks.forEach { c -> repeat(32) { out += c } }
        return out.toByteArray()
    }
}
