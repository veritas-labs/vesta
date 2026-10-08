package uk.veritaslabs.vesta.blobs

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.x0x.X0xClient

/**
 * The blob machinery of one device (ADR-023): the store under [directory], the photo [encoder] of this platform, and the [BlobTransferService] that
 * moves photos to and from the Home's other devices ([peersFor] says which). Built by the platform entry points and handed to `VestaApp`.
 */
class DeviceBlobRuntime(
    database: VestaDatabase,
    client: X0xClient,
    cookbookSources: CookbookSources,
    peersFor: suspend (homeId: String) -> Set<String>,
    directory: File,
    encoder: PhotoEncoder,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    interval: Duration = 30.seconds,
    askBackoff: BlobTransferService.Backoff = BlobTransferService.Backoff(30.seconds, 10.minutes),
) : BlobRuntime {

    private val repository = BlobRepository(BlobStore(directory), database)
    private lateinit var runtimeBlobs: RepositoryBlobs

    private val service = BlobTransferService(
        client = client,
        repository = repository,
        cookbookSources = cookbookSources,
        database = database,
        peersFor = peersFor,
        onArrived = { hash -> runtimeBlobs.notifyArrived(hash) },
        interval = interval,
        askBackoff = askBackoff,
        nowMillis = nowMillis,
    )

    init {
        runtimeBlobs = RepositoryBlobs(BlobIngest(encoder, repository, nowMillis), repository, service.waitingForWifi)
    }

    override val blobs: Blobs get() = runtimeBlobs

    override suspend fun run() = service.run()
}
