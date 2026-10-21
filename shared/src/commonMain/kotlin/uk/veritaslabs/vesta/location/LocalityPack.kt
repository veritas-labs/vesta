package uk.veritaslabs.vesta.location

/**
 * A country's localities as flat parallel arrays indexed by row (22,093 rows in GB, so no object per row).
 * `parent` is -1 for a row without one; neighbours of row `r` are `adjacency[adjacencyStart[r] until adjacencyStart[r + 1]]`.
 */
class LocalityPack internal constructor(
    val size: Int,
    internal val uuids: ByteArray,
    internal val subtypeOf: ByteArray,
    internal val subtypes: List<String>,
    internal val parent: IntArray,
    internal val names: Array<String>,
    internal val latE6: IntArray,
    internal val lonE6: IntArray,
    internal val adjacencyStart: IntArray,
    internal val adjacency: IntArray,
) {
    fun name(row: Int): String = names[row]

    fun subtype(row: Int): String = subtypes[subtypeOf[row].toInt() and 0xFF]

    fun parentRow(row: Int): Int = parent[row]

    /** The row's id as a canonical lowercase 8-4-4-4-12 UUID. */
    fun uuid(row: Int): String {
        val text = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) text.append('-')
            val b = uuids[row * 16 + i].toInt() and 0xFF
            text.append(HEX[b shr 4]).append(HEX[b and 0x0F])
        }
        return text.toString()
    }

    companion object {
        private const val NAMES_MAGIC = "VGN2"
        private const val HEX = "0123456789abcdef"

        fun parse(namesChunk: ByteArray, adjacencyChunk: ByteArray, inflater: Inflater): LocalityPack {
            val names = ByteReader(inflater.inflate(namesChunk))
            require(names.ascii(4) == NAMES_MAGIC) { "Unknown names chunk version" }
            val subtypes = List(names.varint()) { names.cString() }
            val n = names.varint()
            val uuids = names.take(n * 16)
            val subtypeOf = names.take(n)
            val parent = IntArray(n) { names.varint() - 1 }
            val rowNames = Array(n) { names.cString() }
            val lat = IntArray(n)
            var running = 0
            for (i in 0 until n) {
                running += names.zigzag()
                lat[i] = running
            }
            val lon = IntArray(n)
            running = 0
            for (i in 0 until n) {
                running += names.zigzag()
                lon[i] = running
            }
            subtypeOf.forEach { require((it.toInt() and 0xFF) < subtypes.size) { "Subtype index out of range" } }
            parent.forEach { require(it < n) { "Parent row out of range" } }

            val adj = ByteReader(inflater.inflate(adjacencyChunk))
            val start = IntArray(n + 1)
            var data = IntArray(n * 7)
            var count = 0
            for (row in 0 until n) {
                start[row] = count
                val degree = adj.varint()
                if (count + degree > data.size) data = data.copyOf(maxOf(data.size * 2, count + degree))
                var last = row
                repeat(degree) {
                    last += adj.zigzag()
                    require(last in 0 until n) { "Neighbour row out of range" }
                    data[count++] = last
                }
            }
            start[n] = count
            return LocalityPack(n, uuids, subtypeOf, subtypes, parent, rowNames, lat, lon, start, data.copyOf(count))
        }
    }
}
