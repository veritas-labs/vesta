package uk.veritaslabs.vesta.blobs

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import uk.veritaslabs.kits.store.KitsDatabase

/** A hash a document of [homeId] refers to that this device does not hold. */
data class BlobWant(val hash: String, val homeId: String, val size: Long, val firstWantedAt: Long, val lastAskedAt: Long, val attempts: Long)

/**
 * The index over a [BlobStore]: which blobs this device holds and when a document last referred to each, and which it still wants. Times are
 * epoch milliseconds passed in by the caller, so tests control them. The files stay the authority on what is held; [reconcile] repairs the index.
 */
class BlobRepository(private val store: BlobStore, private val database: KitsDatabase) {

    private val queries get() = database.blobQueries

    fun has(hash: String): Boolean = store.has(hash)

    fun file(hash: String) = store.file(hash)

    /** Stores [bytes] and records them as just referenced. */
    suspend fun put(bytes: ByteArray, mime: String, now: Long): BlobInfo =
        store.put(bytes).also { record(it, mime, now) }

    /**
     * Takes a received file into the store (see [BlobStore.importFile]). On success the hash is held and no longer wanted;
     * on a mismatch the want is kept so the blob is asked for again.
     */
    suspend fun importFile(path: String, hash: String, size: Long, mime: String, now: Long): BlobImport =
        store.importFile(path, hash, size).also { result ->
            if (result is BlobImport.Imported) {
                record(result.info, mime, now)
                queries.deleteWant(hash)
            }
        }

    /** Notes that a document refers to [hash] and this device lacks it. Already wanted or already held changes nothing. */
    suspend fun want(hash: String, homeId: String, size: Long, now: Long) {
        if (store.has(hash)) return
        queries.insertWantIfAbsent(hash, homeId, size, now)
    }

    /** The hash is now held (arrived by other means): it stops being wanted. */
    suspend fun markHave(hash: String) {
        queries.deleteWant(hash)
    }

    suspend fun wants(): List<BlobWant> =
        queries.selectAllWants().awaitAsList().map { BlobWant(it.hash, it.homeId, it.size, it.firstWantedAt, it.lastAskedAt, it.attempts) }

    /** Records that peers were just asked for [hash]. */
    suspend fun recordAsk(hash: String, now: Long) {
        queries.recordAsk(now, hash)
    }

    /** A peer came online: every want may be asked again at once. */
    suspend fun resetBackoff() {
        queries.resetBackoff()
    }

    /** A document refers to the held blob [hash] right now. */
    suspend fun touch(hash: String, now: Long) {
        queries.touchBlob(now, hash)
    }

    /** Hashes held and last referenced before [cutoff]: candidates for deletion once nothing refers to them. */
    suspend fun unreferencedSince(cutoff: Long): List<String> =
        queries.selectBlobsNotReferencedSince(cutoff).awaitAsList().map { it.hash }

    /** Deletes the file and the row. */
    suspend fun delete(hash: String) {
        store.delete(hash)
        queries.deleteBlob(hash)
    }

    /**
     * Makes the index match the files after a crash: a file with no row is adopted (as [mime] "application/octet-stream" unless known, referenced [now]),
     * a row whose file is gone is dropped, and a want for a hash that is now held is cleared. Also clears the store's stray temp files.
     */
    suspend fun reconcile(now: Long) {
        store.sweepTemporaryFiles()
        val onDisk = store.all().associateBy { it.hash }
        val rows = queries.selectAllBlobs().awaitAsList()
        rows.filter { it.hash !in onDisk }.forEach { queries.deleteBlob(it.hash) }
        val known = rows.map { it.hash }.toSet()
        onDisk.values.filter { it.hash !in known }.forEach { queries.upsertBlob(it.hash, it.size, UnknownMime, now) }
        onDisk.keys.forEach { queries.deleteWant(it) }
    }

    /** The row for [hash], or null when this device holds no such blob. */
    suspend fun mimeOf(hash: String): String? = queries.selectBlob(hash).awaitAsOneOrNull()?.mime

    private suspend fun record(info: BlobInfo, mime: String, now: Long) {
        queries.upsertBlob(info.hash, info.size, mime, now)
    }

    private companion object {
        const val UnknownMime = "application/octet-stream"
    }
}
