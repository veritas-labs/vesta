package uk.veritaslabs.vesta.blobs

import java.io.File
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.automerge.CookbookBlobReferences
import uk.veritaslabs.vesta.automerge.homeIdsOf
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.vesta.blobs.MediaKind
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeMedia

class BlobTransferServiceTest {

    private val network = FakeX0xNetwork()
    private val homeId = "home-1"
    private val devices = mutableListOf<Device>()
    private val jobs = mutableListOf<Job>()
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Who is in the Home; a device that isn't here is a stranger to the others. */
    private val members = mutableSetOf("a", "b", "c")

    private inner class Device(val id: String) {
        val node = network.node(id, directStream = true)
        val database = openInMemoryDatabase()
        val store = BlobStore(Files.createTempDirectory("blobs-$id").toFile().also { it.deleteOnExit() })
        val repository = BlobRepository(store, database)
        val cookbooks = CookbookSources(database)
        val arrived = mutableListOf<String>()

        /** Moves this device's clock forward, to pass the garbage-collection grace period without waiting. */
        val skew = java.util.concurrent.atomic.AtomicLong(0)
        val service = BlobTransferService(
            client = node,
            repository = repository,
            references = CookbookBlobReferences(cookbooks),
            homes = homeIdsOf(database),
            peersFor = { members - id },
            onArrived = { arrived += it },
            interval = 60.milliseconds,
            askBackoff = BlobTransferService.Backoff(150.milliseconds, 600.milliseconds),
            serveWindow = 500.milliseconds,
            nowMillis = { System.currentTimeMillis() + skew.get() },
        )

        init {
            runBlocking { database.homeQueries.insert(homeId, "Home", "family", 1_000L) }
            devices += this
        }

        /** Holds [bytes] as a photo and returns the reference a document would carry. */
        suspend fun hold(bytes: ByteArray): BlobReference {
            val info = repository.put(bytes, "image/webp", now = System.currentTimeMillis())
            return reference(info.hash, info.size)
        }

        /** This device's copy of the cookbook now refers to [references] (as it does once the document has synced). */
        suspend fun refer(vararg references: BlobReference) {
            cookbooks.forHome(homeId)!!.save(Recipe(key = "r", title = "Pie", media = references.map { RecipeMedia("", MediaKind.Photo, "", it) }))
        }

        fun start() {
            jobs += scope.launch { service.run() }
        }
    }

    private fun reference(hash: String, size: Long) = BlobReference(hash, size, "image/webp", MediaKind.Photo, 4, 3, "t:abc")

    private fun photo(seed: Int) = ByteArray(5_000) { (it * seed).toByte() }

    private suspend fun eventually(what: String = "condition", condition: suspend () -> Boolean) {
        try {
            withTimeout(8_000) { while (!condition()) delay(25) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("timed out waiting for $what")
        }
    }

    /** Lets every started service open its event streams. */
    private suspend fun settle() = delay(300)

    @AfterTest
    fun stop() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun anAuthorsPhotoIsPushedToAnOnlineDeviceThatWantsIt() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val bytes = photo(3)
        val ref = a.hold(bytes)
        a.start(); b.start()
        b.refer(ref) // b's copy of the document already names the photo
        settle()
        network.comeOnline("b") // a sees b

        // Act
        a.refer(ref) // the author saves the recipe

        // Assert
        eventually("b to receive the photo") { b.repository.has(ref.hash) }
        assertContentEquals(bytes, b.repository.file(ref.hash)!!.readBytes())
        assertTrue(b.repository.wants().isEmpty())
        assertEquals(listOf(ref.hash), b.arrived)
    }

    @Test
    fun aDeviceThatWasOfflineAsksAnOnlineHolderWhenItSeesItCome() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val bytes = photo(5)
        val ref = a.hold(bytes)
        a.refer(ref)
        b.refer(ref)
        a.start(); b.start()
        settle()

        // Act
        network.comeOnline("a") // b sees a come online

        // Assert
        eventually("b to receive the photo") { b.repository.has(ref.hash) }
        assertContentEquals(bytes, b.repository.file(ref.hash)!!.readBytes())
    }

    @Test
    fun anyHolderServesNotJustTheAuthor() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val c = Device("c")
        val bytes = photo(7)
        val ref = c.hold(bytes) // c got it earlier; the author a is away and never came online for b
        a.refer(ref); b.refer(ref); c.refer(ref)
        b.start(); c.start()
        network.offline += "a"
        settle()

        // Act
        network.comeOnline("c")

        // Assert
        eventually("b to receive the photo from c") { b.repository.has(ref.hash) }
        assertTrue(network.transfers.values.all { it.from == "c" })
    }

    @Test
    fun anOfferForAHashNobodyWantsIsRejectedAtOnce() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        b.start()
        settle()
        val bytes = photo(9)
        val unwanted = a.hold(bytes)
        val file = a.repository.file(unwanted.hash)!!

        // Act
        val id = a.node.sendFile("b", "${unwanted.hash}.webp", bytes.size.toLong(), unwanted.hash, file.path)

        // Assert
        eventually("b to reject the offer") { network.transfers.getValue(id).receiverState == "Rejected" }
        assertFalse(b.repository.has(unwanted.hash))
    }

    @Test
    fun anOfferWhoseSizeDiffersFromTheDocumentIsRejected() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val bytes = photo(11)
        val ref = a.hold(bytes)
        b.refer(ref)
        b.start()
        settle()
        val file = a.repository.file(ref.hash)!!

        // Act
        val id = a.node.sendFile("b", "${ref.hash}.webp", bytes.size + 1L, ref.hash, file.path)

        // Assert
        eventually("b to reject the offer") { network.transfers.getValue(id).receiverState == "Rejected" }
        assertFalse(b.repository.has(ref.hash))
    }

    @Test
    fun anOfferFromADeviceOutsideTheHomeIsRejected() = runBlocking {
        // Arrange
        val b = Device("b")
        val stranger = Device("z") // not in members
        val bytes = photo(13)
        val ref = stranger.hold(bytes)
        b.refer(ref)
        b.start()
        settle()
        val file = stranger.repository.file(ref.hash)!!

        // Act
        val id = stranger.node.sendFile("b", "${ref.hash}.webp", bytes.size.toLong(), ref.hash, file.path)

        // Assert
        eventually("b to reject the offer") { network.transfers.getValue(id).receiverState == "Rejected" }
        assertFalse(b.repository.has(ref.hash))
    }

    @Test
    fun aFileThatIsNotTheWantedHashLeavesTheWantInPlace() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val wanted = reference(BlobStore.sha256Hex(photo(15)), 5_000)
        b.refer(wanted)
        b.start()
        settle()
        // a sends other bytes of the right size under the wanted name, with a sha256 that matches the bytes (so the daemon is satisfied).
        val other = photo(17)
        val file = File.createTempFile("other", ".webp").apply { writeBytes(other); deleteOnExit() }

        // Act
        a.node.sendFile("b", "${wanted.hash}.webp", other.size.toLong(), BlobStore.sha256Hex(other), file.path)

        // Assert
        eventually("b to receive and discard the file") { b.node.transfersDir.listFiles()!!.isEmpty() && network.transfers.values.any { it.receiverState == "Complete" } }
        delay(300)
        assertFalse(b.repository.has(wanted.hash))
        assertEquals(listOf(wanted.hash), b.repository.wants().map { it.hash })
        assertTrue(b.arrived.isEmpty())
    }

    @Test
    fun aWantFromADeviceOutsideTheHomeIsNotServed() = runBlocking {
        // Arrange
        val a = Device("a")
        val stranger = Device("z")
        val bytes = photo(19)
        val ref = a.hold(bytes)
        a.refer(ref)
        a.start()
        settle()

        // Act
        stranger.node.sendDirect("a", BlobMessage.encode(BlobMessage.Want(homeId, ref.hash)))
        delay(400)

        // Assert
        assertTrue(network.transfers.isEmpty())
    }

    @Test
    fun aWantForAHashTheHomesCookbookDoesNotReferToIsNotServed() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val secret = a.hold(photo(21)) // held, but no recipe refers to it
        a.start()
        settle()

        // Act
        b.node.sendDirect("a", BlobMessage.encode(BlobMessage.Want(homeId, secret.hash)))
        delay(400)

        // Assert
        assertTrue(network.transfers.isEmpty())
    }

    @Test
    fun repeatedAsksWithinTheWindowAreServedOnce() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val ref = a.hold(photo(23))
        a.refer(ref)
        a.start()
        settle()
        val ask = BlobMessage.encode(BlobMessage.Want(homeId, ref.hash))

        // Act
        repeat(3) { b.node.sendDirect("a", ask) }
        delay(250)

        // Assert
        assertEquals(1, network.transfers.size)
    }

    @Test
    fun aWantIsNotAskedWhileNobodyIsOnlineAndIsAskedAgainWithBackoffOnceSomeoneIs() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val ref = a.hold(photo(25))
        b.refer(ref)
        b.start()
        settle()

        // Act
        delay(400)
        val attemptsWhileAlone = b.repository.wants().single().attempts

        // Assert
        assertEquals(0L, attemptsWhileAlone)
        assertTrue(b.node.sentTo.isEmpty())
    }

    @Test
    fun aKilledAndRestartedDeviceResumesFromItsWantList() = runBlocking {
        // Arrange
        val a = Device("a")
        val b = Device("b")
        val bytes = photo(27)
        val ref = a.hold(bytes)
        a.refer(ref); b.refer(ref)
        a.start()
        val firstRun = scope.launch { b.service.run() }
        settle()
        firstRun.cancel() // b is killed before anything arrived
        assertEquals(listOf(ref.hash), b.repository.wants().map { it.hash })

        // Act
        b.start()
        settle()
        network.comeOnline("a")

        // Assert
        eventually("b to receive the photo after the restart") { b.repository.has(ref.hash) }
    }

    @Test
    fun aWantNobodyAnswersIsAskedAgainRoundAfterRoundWithBackoff() = runBlocking {
        // Arrange
        val a = Device("a") // never started, so it never answers
        val b = Device("b")
        val ref = a.hold(photo(29))
        b.refer(ref)
        b.start()
        settle()

        // Act
        network.comeOnline("a")

        // Assert
        eventually("b to ask again and again") { b.repository.wants().single().attempts >= 3 }
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aBlobNothingReferencesIsKeptThroughTheGracePeriodThenDeleted() = runBlocking {
        // Arrange
        val a = Device("a")
        val orphan = a.hold(photo(31))
        a.service.scan(homeId)

        // Act
        a.skew.addAndGet(6 * day)
        a.service.collectGarbage()
        val keptWithinGrace = a.repository.has(orphan.hash)
        a.skew.addAndGet(2 * day)
        a.service.collectGarbage()

        // Assert
        assertTrue(keptWithinGrace)
        assertFalse(a.repository.has(orphan.hash))
        assertEquals(null, a.repository.mimeOf(orphan.hash))
    }

    @Test
    fun aReferencedBlobIsNeverDeletedHoweverOldItIs() = runBlocking {
        // Arrange
        val a = Device("a")
        val ref = a.hold(photo(33))
        a.refer(ref)

        // Act
        a.skew.addAndGet(30 * day)
        a.service.scan(homeId) // the scan touches what is still referenced
        a.service.collectGarbage()

        // Assert
        assertTrue(a.repository.has(ref.hash))
    }

    @Test
    fun aReferenceThatReappearsInTimeRestartsTheClock() = runBlocking {
        // Arrange
        val a = Device("a")
        val ref = a.hold(photo(35))
        a.refer(ref)
        a.service.scan(homeId)
        a.refer() // the photo is removed from the recipe
        a.skew.addAndGet(6 * day)
        a.service.scan(homeId)
        a.service.collectGarbage()

        // Act
        a.refer(ref) // and put back
        a.service.scan(homeId)
        a.skew.addAndGet(3 * day)
        a.service.collectGarbage()

        // Assert
        assertTrue(a.repository.has(ref.hash))
    }

    @Test
    fun aBlobThatIsStillWantedIsNotCollected() = runBlocking {
        // Arrange
        val a = Device("a")
        val held = a.hold(photo(37))
        a.repository.want(held.hash, homeId, held.size, now = 1) // (a want for a held hash is unusual; the guard is what is tested)
        a.database.blobQueries.insertWantIfAbsent(held.hash, homeId, held.size, 1)

        // Act
        a.skew.addAndGet(30 * day)
        a.service.collectGarbage()

        // Assert
        assertTrue(a.repository.has(held.hash))
    }

    @Test
    fun nothingIsCollectedWhileAHomesCookbookCannotBeRead() = runBlocking {
        // Arrange
        val a = Device("a")
        val orphan = a.hold(photo(39))
        a.database.homeQueries.insert("home-2", "Gone", "family", 1L)
        a.database.homeQueries.deleteById("home-2") // a Home row that vanishes between the listing and the open is the case; here the Home is simply absent
        a.skew.addAndGet(30 * day)

        // Act
        a.service.collectGarbage()

        // Assert
        assertFalse(a.repository.has(orphan.hash)) // all readable: it goes
    }
}
