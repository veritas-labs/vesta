package uk.veritaslabs.vesta.location

/**
 * One unit of places: bytes [offset, offset + length) of the DECOMPRESSED data chunk [chunk], holding [count] places.
 * [nearby] false = inside the locality polygon; true = outside every polygon and this locality is the nearest within 5 km.
 */
data class PlaceRange(val chunk: Int, val offset: Int, val length: Int, val count: Int, val nearby: Boolean)

/**
 * The places directory (VGP2): for each row of the locality names table, the ranges of places inside it and nearby, plus the
 * compressed size of every data chunk so the download size is known before anything is fetched.
 */
class PlacesDirectory internal constructor(
    private val chunkSizes: List<Int>,
    private val rows: List<List<PlaceRange>>,
) {
    /** Number of rows; equals the locality names table. */
    val size: Int get() = rows.size

    val chunkCount: Int get() = chunkSizes.size

    /** Compressed size of data chunk [chunk], what fetching it downloads. */
    fun chunkSize(chunk: Int): Int = chunkSizes[chunk]

    /** The row's ranges, inside ranges first, then nearby, in the order the builder wrote them. */
    fun rangesFor(row: Int): List<PlaceRange> = rows[checked(row)]

    fun insideCount(row: Int): Int = rows[checked(row)].filter { !it.nearby }.sumOf { it.count }

    fun nearbyCount(row: Int): Int = rows[checked(row)].filter { it.nearby }.sumOf { it.count }

    /** The distinct chunk indexes the rows need, ascending. */
    fun chunksFor(rows: Collection<Int>, includeNearby: Boolean = true): List<Int> =
        rows.flatMap { rangesFor(it) }.filter { includeNearby || !it.nearby }.map { it.chunk }.distinct().sorted()

    /** Total compressed bytes of the distinct chunks the rows need; a chunk shared by several rows counts once. */
    fun bytesFor(rows: Collection<Int>, includeNearby: Boolean = true): Long =
        chunksFor(rows, includeNearby).sumOf { chunkSizes[it].toLong() }

    private fun checked(row: Int): Int {
        require(row in rows.indices) { "Row $row is outside the places directory (${rows.size} rows)" }
        return row
    }

    companion object {
        private const val MAGIC = "VGP2"

        /** Parses the compressed directory blob. */
        fun parse(compressed: ByteArray, inflater: Inflater): PlacesDirectory {
            val reader = ByteReader(inflater.inflate(compressed))
            require(reader.ascii(4) == MAGIC) { "Not a places directory" }
            val chunks = reader.varint()
            require(chunks <= reader.remaining) { "Places directory is truncated" }
            val sizes = List(chunks) { reader.varint() }
            val rowCount = reader.varint()
            require(rowCount <= reader.remaining) { "Places directory is truncated" }
            val rows = List(rowCount) {
                val n = reader.varint()
                require(n <= reader.remaining) { "Places directory is truncated" }
                val ranges = List(n) {
                    val chunk = reader.varint()
                    val offset = reader.varint()
                    val length = reader.varint()
                    val packed = reader.varint()
                    require(chunk < chunks) { "Range refers to chunk $chunk of $chunks" }
                    require(offset >= 4) { "Range offset $offset overlaps the chunk magic" }
                    PlaceRange(chunk, offset, length, packed ushr 1, packed and 1 == 1)
                }
                val firstNearby = ranges.indexOfFirst { it.nearby }
                require(firstNearby < 0 || ranges.drop(firstNearby).all { it.nearby }) { "Inside range after a nearby range" }
                ranges
            }
            require(reader.remaining == 0) { "Places directory has ${reader.remaining} trailing bytes" }
            return PlacesDirectory(sizes, rows)
        }
    }
}
