package uk.veritaslabs.vesta.location

/** Inflates a zlib stream. The locality chunks are zlib; common code has no inflate, so each platform supplies one. */
fun interface Inflater {
    fun inflate(zlib: ByteArray): ByteArray
}
