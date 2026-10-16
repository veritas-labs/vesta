@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.automerge.CookbookBlobReferences
import uk.veritaslabs.vesta.automerge.homeIdsOf
import uk.veritaslabs.kits.blobs.BlobReference
import uk.veritaslabs.kits.blobs.BlobRepository
import uk.veritaslabs.kits.blobs.BlobStore
import uk.veritaslabs.kits.blobs.BlobTransferService
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.kits.blobs.MediaKind
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeMedia

/**
 * Moves a photo between two real x0xd daemons (docs/plans/ADR-023-implementation-plan.md, WP6): two "phones" running the real
 * [DeviceDataSyncService] and a [BlobTransferService] each. Skipped unless `VESTA_X0X_BLOB_NAMES` names two x0x instances (comma separated:
 * phone A, phone B); the test starts and stops them itself, so use names nothing else uses (see [HubDaemonIntegrationTest] for what that leaves behind). Run with:
 * `VESTA_X0X_BLOB_NAMES=vblob-a,vblob-b ./gradlew :shared:jvmTest --tests '*BlobTransferDaemonIntegrationTest'`.
 *
 * A and B share a Home. A adds a recipe whose photo only A holds; the recipe reaches B through the ordinary cookbook sync, which makes B want
 * the photo and ask A for it; A serves it by native file transfer; B ends up holding the same bytes, and the daemon's copy is gone.
 */
class BlobTransferDaemonIntegrationTest {

    @Test
    fun aPhotoOnlyOnePhoneHoldsReachesTheOtherThroughTheDaemons() {
        val names = System.getenv("VESTA_X0X_BLOB_NAMES")?.split(',')?.map { it.trim() }?.takeIf { it.size == 2 } ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = Phone(Daemon(names[0]), scope)
        val b = Phone(Daemon(names[1]), scope)
        try {
            runBlocking {
                withTimeout(15.minutes) {
                    // Arrange: A made the Home and admitted B; both phones are open, each with its own blob store.
                    a.daemon.start()
                    b.daemon.start()
                    val aId = a.agentId()
                    val bId = b.agentId()
                    val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
                    b.joinedHome(homeId)
                    a.sync.admit(homeId, b.profiles.current().id, b.profiles.current().displayName, "member", bId)
                    b.sync.admit(homeId, a.profiles.current().id, a.profiles.current().displayName, "admin", aId)
                    a.open()
                    b.open()

                    val repositoryA = BlobRepository(BlobStore(Files.createTempDirectory("blob-a").toFile()), a.database)
                    val repositoryB = BlobRepository(BlobStore(Files.createTempDirectory("blob-b").toFile()), b.database)
                    val arrivedB = mutableListOf<String>()
                    fun service(phone: Phone, repository: BlobRepository, arrived: (String) -> Unit = {}) = BlobTransferService(
                        client = phone.daemon.client,
                        repository = repository,
                        references = CookbookBlobReferences(phone.cookbooks),
                        spaces = homeIdsOf(phone.database),
                        peersFor = { phone.sync.blobPeers(it) },
                        onArrived = arrived,
                        interval = 5.seconds,
                        askBackoff = BlobTransferService.Backoff(5.seconds, 30.seconds),
                    )
                    scope.launch { service(a, repositoryA).run() }
                    scope.launch { service(b, repositoryB) { arrivedB += it }.run() }

                    // Act: A has a photo and a recipe that shows it.
                    val bytes = ByteArray(150_000) { (it * 31).toByte() }
                    val info = repositoryA.put(bytes, "image/webp", now = System.currentTimeMillis())
                    val reference = BlobReference(info.hash, info.size, "image/webp", MediaKind.Photo, 4, 3, "t:abc")
                    a.cookbooks.forHome(homeId)!!.save(Recipe(title = "Pie", media = listOf(RecipeMedia("", MediaKind.Photo, "Fresh", reference))))

                    // Assert: B learns of the recipe, wants the photo, and gets it from A.
                    until("B to hold the photo", seconds = 600, seen = { "wants ${repositoryB.wants().size}, recipes ${b.cookbooks.forHome(homeId)?.recipes?.first()?.size}" }) {
                        repositoryB.has(info.hash)
                    }
                    assertContentEquals(bytes, repositoryB.file(info.hash)!!.readBytes())
                    until("B to have no want left") { repositoryB.wants().isEmpty() }
                    assertContentEquals(listOf(info.hash), arrivedB)
                }
            }
        } finally {
            runBlocking {
                runCatching { a.close() }
                runCatching { b.close() }
            }
            scope.cancel()
        }
    }
}
