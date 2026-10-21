package uk.veritaslabs.vesta.location

/** One place from a data chunk. Only what the pack carries: no contact or address data. */
data class PlaceRecord(
    /** UUID string with dashes. */
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** 0.0 to 1.0 (0.0 when the source had none). */
    val confidence: Double,
    /** Index into the [CategoryTable]. */
    val categoryIndex: Int,
    val nearby: Boolean,
)

/** Decoders for VGD1 data chunks and their units. */
object PlaceUnit {
    private const val MAGIC = "VGD1"

    /** Inflates a compressed data chunk and checks its magic. The result is what [PlaceRange] offsets index into. */
    fun decompress(compressed: ByteArray, inflater: Inflater): ByteArray {
        val bytes = inflater.inflate(compressed)
        require(bytes.size >= 4 && bytes.copyOfRange(0, 4).decodeToString() == MAGIC) { "Not a places data chunk" }
        return bytes
    }

    /** Decodes the unit [range] points at inside a decompressed chunk; every place gets the range's nearby flag. */
    fun decodeRange(decompressedChunk: ByteArray, range: PlaceRange): List<PlaceRecord> {
        require(range.offset >= 4 && range.length >= 0 && range.offset.toLong() + range.length <= decompressedChunk.size) {
            "Range ${range.offset}+${range.length} is outside a chunk of ${decompressedChunk.size} bytes"
        }
        val reader = ByteReader(decompressedChunk.copyOfRange(range.offset, range.offset + range.length))
        val n = reader.varint()
        require(n == range.count) { "Unit holds $n places but the directory says ${range.count}" }
        require(n.toLong() * 16 <= reader.remaining) { "Places unit is truncated" }
        val ids = List(n) { reader.take(16).toUuid() }
        val lat = deltas(reader, n)
        val lon = deltas(reader, n)
        val categories = List(n) { reader.varint() }
        val confidence = List(n) { reader.u8() }
        val names = List(n) { reader.cString() }
        require(reader.remaining == 0) { "Places unit has ${reader.remaining} trailing bytes" }
        return List(n) {
            PlaceRecord(ids[it], names[it], lat[it] / 1e6, lon[it] / 1e6, confidence[it] / 255.0, categories[it], range.nearby)
        }
    }

    private fun deltas(reader: ByteReader, n: Int): List<Int> {
        var value = 0
        return List(n) {
            value += reader.zigzag()
            value
        }
    }

    private fun ByteArray.toUuid(): String {
        val hex = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
