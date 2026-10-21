package uk.veritaslabs.vesta.location

/** The root chunk: names which release and country the pack is for and where its other chunks live. */
data class LocalityRoot(
    val release: String,
    val country: String,
    val namesAddress: String,
    val adjacencyAddress: String,
) {
    companion object {
        private const val MAGIC = "VGI2"

        /** Parses the uncompressed root chunk. The cell-index shard table is skipped, not returned. */
        fun parse(bytes: ByteArray): LocalityRoot {
            val reader = ByteReader(bytes)
            require(reader.ascii(4) == MAGIC) { "Not a locality root chunk" }
            reader.u8() // shard resolution
            val release = reader.ascii(16).trimEnd()
            val country = reader.ascii(2)
            val names = reader.take(32).toHex()
            val adjacency = reader.take(32).toHex()
            val shards = reader.u8() or (reader.u8() shl 8)
            reader.take(shards * 36) // checks the table is all there; shards are not used in this slice
            return LocalityRoot(release, country, names, adjacency)
        }

        private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
