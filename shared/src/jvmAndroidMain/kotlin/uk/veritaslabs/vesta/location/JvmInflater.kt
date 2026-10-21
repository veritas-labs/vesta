package uk.veritaslabs.vesta.location

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater as ZipInflater

/** [Inflater] over `java.util.zip`, for desktop and Android. */
class JvmInflater : Inflater {
    override fun inflate(zlib: ByteArray): ByteArray {
        val zip = ZipInflater()
        try {
            zip.setInput(zlib)
            val out = ByteArrayOutputStream(zlib.size * 4)
            val buffer = ByteArray(64 * 1024)
            while (!zip.finished()) {
                val read = try {
                    zip.inflate(buffer)
                } catch (e: DataFormatException) {
                    throw IllegalArgumentException("Not a valid zlib stream: ${e.message}", e)
                }
                if (read == 0 && (zip.needsInput() || zip.needsDictionary())) {
                    throw IllegalArgumentException("Truncated zlib stream")
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        } finally {
            zip.end()
        }
    }
}
