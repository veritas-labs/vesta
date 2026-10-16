package uk.veritaslabs.kits.blobs

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A stored blob: its SHA-256 as 64 lowercase hex characters, and its length in bytes. */
data class BlobInfo(val hash: String, val size: Long)

/** How [BlobStore.importFile] ended. The source file is gone in every case except [SourceMissing]. */
sealed interface BlobImport {
    data class Imported(val info: BlobInfo, val alreadyHeld: Boolean) : BlobImport

    /** The bytes did not hash to what was expected, or were not the expected length; nothing was stored. */
    data class Mismatch(val actualHash: String, val actualSize: Long) : BlobImport

    data object SourceMissing : BlobImport
}

/**
 * Content-addressed files under [root]: a blob is `<root>/<first two hex>/<hash>`. Only a file that was complete and verified is ever at that
 * path, because bytes are written to `<root>/tmp` first and moved in with one atomic rename, so a crash leaves at worst a stray temp file
 * (swept by [sweepTemporaryFiles]) and never a half-written blob that [has] would report.
 */
class BlobStore(private val root: File) {

    private val tmp = File(root, "tmp")

    init {
        tmp.mkdirs()
    }

    fun has(hash: String): Boolean = file(hash)?.isFile == true

    /** Where [hash] is or would be stored; null when [hash] is not a SHA-256 in lowercase hex (so a name can never escape [root]). */
    fun file(hash: String): File? = hash.takeIf(::isHash)?.let { File(File(root, it.take(2)), it) }

    /** Stores [bytes] under their own hash. Storing the same bytes again changes nothing. */
    fun put(bytes: ByteArray): BlobInfo {
        val info = BlobInfo(sha256Hex(bytes), bytes.size.toLong())
        if (has(info.hash)) return info
        val staged = File.createTempFile("put", ".part", tmp)
        try {
            staged.writeBytes(bytes)
            place(staged, info.hash)
        } finally {
            staged.delete()
        }
        return info
    }

    /**
     * Moves the file at [path] into the store if it hashes to [expectedHash] and is [expectedSize] bytes. The bytes are hashed while they are copied
     * to a temp file in the store, so the source is read once and a mismatch leaves nothing behind. The source is deleted on success and on mismatch:
     * it is the x0x daemon's copy, and the daemon never cleans up after itself (ADR-023, Spike 4).
     */
    fun importFile(path: String, expectedHash: String, expectedSize: Long): BlobImport {
        val source = File(path)
        if (!source.isFile) return BlobImport.SourceMissing
        if (!isHash(expectedHash)) {
            source.delete()
            return BlobImport.Mismatch(actualHash = "", actualSize = source.length())
        }
        val staged = File.createTempFile("import", ".part", tmp)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            source.inputStream().use { input ->
                staged.outputStream().use { output ->
                    val buffer = ByteArray(BufferSize)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        size += read
                    }
                }
            }
            val actual = digest.digest().toHex()
            if (actual != expectedHash || size != expectedSize) {
                source.delete()
                return BlobImport.Mismatch(actual, size)
            }
            val alreadyHeld = has(expectedHash)
            if (!alreadyHeld) place(staged, expectedHash)
            source.delete()
            return BlobImport.Imported(BlobInfo(expectedHash, size), alreadyHeld)
        } finally {
            staged.delete()
        }
    }

    fun delete(hash: String) {
        file(hash)?.delete()
    }

    /** The hash and size of every blob on disk, for reconciling the files with the `Blob` table. */
    fun all(): List<BlobInfo> =
        root.listFiles { f -> f.isDirectory && f.name.length == 2 }.orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.isFile && isHash(f.name) && f.name.startsWith(dir.name) }.orEmpty().toList() }
            .map { BlobInfo(it.name, it.length()) }
            .sortedBy { it.hash }

    /** Removes temp files left by a crash. Call at start-up, before anything is writing. */
    fun sweepTemporaryFiles() {
        tmp.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun place(staged: File, hash: String) {
        val target = checkNotNull(file(hash))
        target.parentFile.mkdirs()
        try {
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // Another writer stored the same bytes first; theirs is verified too, so keep it.
            if (!target.isFile) throw e
        }
    }

    companion object {
        private const val BufferSize = 64 * 1024
        private val HashPattern = Regex("[0-9a-f]{64}")

        fun isHash(text: String): Boolean = HashPattern.matches(text)

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
