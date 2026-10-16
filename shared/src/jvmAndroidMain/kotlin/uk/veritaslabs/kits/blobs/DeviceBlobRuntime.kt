package uk.veritaslabs.kits.blobs

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.x0x.X0xClient

/**
 * The blob machinery of one device (ADR-023): the store under [directory], [references] (which blobs a Space's documents refer to) and [spaces] (every Space's id), the photo [encoder] of this platform, and the [BlobTransferService] that
 * moves photos to and from the Space's other devices ([peersFor] says which). [transfersDir] is the x0x daemon's `transfers/` folder, which the service sweeps ([TransfersJanitor]); null when this device can't reach it. Built by the platform entry points and handed to `VestaApp`.
 */
class DeviceBlobRuntime(
    database: KitsDatabase,
    client: X0xClient,
    references: BlobReferenceSource,
    spaces: suspend () -> List<String>,
    peersFor: suspend (spaceId: String) -> Set<String>,
    directory: File,
    encoder: PhotoEncoder,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    interval: Duration = 30.seconds,
    askBackoff: BlobTransferService.Backoff = BlobTransferService.Backoff(30.seconds, 10.minutes),
    transfersDir: File? = null,
    transfersMinAge: Duration = 15.minutes,
) : BlobRuntime {

    private val repository = BlobRepository(BlobStore(directory), database)
    private lateinit var runtimeBlobs: RepositoryBlobs

    private val service = BlobTransferService(
        client = client,
        repository = repository,
        references = references,
        spaces = spaces,
        peersFor = peersFor,
        onArrived = { hash -> runtimeBlobs.notifyArrived(hash) },
        interval = interval,
        askBackoff = askBackoff,
        nowMillis = nowMillis,
        transfersDir = transfersDir,
        transfersMinAge = transfersMinAge,
    )

    init {
        runtimeBlobs = RepositoryBlobs(BlobIngest(encoder, repository, nowMillis), repository, service.waitingForWifi)
    }

    override val blobs: Blobs get() = runtimeBlobs

    override suspend fun run() = service.run()
}
