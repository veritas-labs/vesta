package uk.veritaslabs.kits.blobs

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import uk.veritaslabs.kits.x0x.X0xClient

/**
 * Sweeps the x0x daemon's `transfers/` folder (docs/adr/ADR-023-blob-storage-and-transfer.md, Spike 4). The daemon never deletes what it lands there,
 * keeps no record of a transfer across a restart, and leaves the `.part` of a receive that was killed (Android does this routinely), so the app
 * cleans up after it. Only files of the shapes the daemon is known to write are touched; anything else in the folder is left alone.
 *
 * * `<transfer id>.part`: deleted once older than [minAge] unless the daemon still lists that transfer as running.
 * * `<id8>_<hash>.webp`, a finished photo nobody moved (the app was killed between `file:complete` and the move): imported if the hash is still
 *   wanted ([BlobRepository.importFile] hashes it again), deleted otherwise. A hash that is in the store already is simply deleted.
 * * `<transfer id>.send` (a `data_b64` send, which Vesta never makes): deleted once older than [minAge].
 *
 * Anything younger than [minAge], or whose transfer this device is in the middle of accepting, is left for the normal path.
 */
internal class TransfersJanitor(
    private val directory: File,
    private val client: X0xClient,
    private val repository: BlobRepository,
    private val minAge: Duration = 15.minutes,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val onArrived: (hash: String) -> Unit = {},
) {
    /** One sweep. [accepting] are the ids of the transfers this device is waiting on. Returns how many files it dealt with. */
    suspend fun sweep(accepting: Set<String>): Int {
        val files = directory.listFiles()?.filter { it.isFile } ?: return 0
        val now = nowMillis()
        var running: Set<String>? = null
        var dealtWith = 0
        for (file in files) {
            if (now - file.lastModified() < minAge.inWholeMilliseconds) continue
            val name = file.name
            val handled = when {
                name.endsWith(".part") -> name.removeSuffix(".part").let { id ->
                    id !in accepting && id !in (running ?: runningTransfers().also { running = it }) && file.delete()
                }
                name.endsWith(".send") -> file.delete()
                else -> finished(file, accepting)
            }
            if (handled) dealtWith++
        }
        return dealtWith
    }

    private suspend fun runningTransfers(): Set<String> =
        runCatching { client.listFileTransfers() }.getOrDefault(emptyList())
            .filter { it.state == "Pending" || it.state == "InProgress" || it.state == "Accepted" }
            .map { it.transferId }.toSet()

    private suspend fun finished(file: File, accepting: Set<String>): Boolean {
        val match = Finished.matchEntire(file.name) ?: return false
        val (id8, hash) = match.destructured
        if (accepting.any { it.startsWith(id8) }) return false
        val want = repository.wants().firstOrNull { it.hash == hash }
        if (want == null || repository.has(hash)) return file.delete()
        val result = repository.importFile(file.path, hash, want.size, PhotoLimits.MimeType, nowMillis())
        return when (result) {
            is BlobImport.Imported -> { onArrived(hash); true }
            else -> file.delete() // not the photo it claimed to be; the want stays and it is asked for again
        }
    }

    private companion object {
        val Finished = Regex("([0-9a-f]{8})_([0-9a-f]{64})\\.webp")
    }
}
