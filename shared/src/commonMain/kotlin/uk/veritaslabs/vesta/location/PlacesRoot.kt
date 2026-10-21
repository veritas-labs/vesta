package uk.veritaslabs.vesta.location

/** The places pack root (VGP1, uncompressed): which locality release it was built against and where its other chunks live. */
data class PlacesRoot(
    val release: String,
    val country: String,
    val directoryAddress: String,
    val categoriesAddress: String,
    /** Data chunk addresses (lowercase hex); the directory's chunk index is a position in this list. */
    val chunkAddresses: List<String>,
) {
    companion object {
        private const val MAGIC = "VGP1"

        fun parse(bytes: ByteArray): PlacesRoot {
            val reader = ByteReader(bytes)
            require(reader.ascii(4) == MAGIC) { "Not a places root chunk" }
            val release = reader.ascii(16).trimEnd()
            val country = reader.ascii(2)
            val directory = reader.take(32).toHex()
            val categories = reader.take(32).toHex()
            val count = reader.take(4).let { (0 until 4).sumOf { i -> (it[i].toLong() and 0xFF) shl (8 * i) } }
            require(count * 32 == reader.remaining.toLong()) { "Places root lists $count chunks but holds ${reader.remaining} bytes of addresses" }
            val chunks = List(count.toInt()) { reader.take(32).toHex() }
            return PlacesRoot(release, country, directory, categories, chunks)
        }

        private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
