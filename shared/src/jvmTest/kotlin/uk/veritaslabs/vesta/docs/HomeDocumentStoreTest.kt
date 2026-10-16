package uk.veritaslabs.vesta.docs

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.SyncState
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase

class HomeDocumentStoreTest {

    private class TestSource(
        val document: Document,
        private val persist: suspend (ByteArray) -> Unit,
        private val onLocalChange: () -> Unit,
    ) : HomeDocument {
        suspend fun refresh() = persist(document.save())

        suspend fun edit(key: String) {
            val tx = document.startTransaction()
            tx.set(ObjectId.ROOT, key, "v")
            tx.commit()
            persist(document.save())
            onLocalChange()
        }

        fun has(key: String): Boolean = document.get(ObjectId.ROOT, key).getOrNull() != null

        override suspend fun generateSync(state: SyncState): ByteArray? = null
        override suspend fun receiveSync(state: SyncState, message: ByteArray) = Unit
        override suspend fun isInSync(state: SyncState) = true
        override suspend fun heads() = ByteArray(0)
        override suspend fun changesSince(saved: ByteArray) = ByteArray(0)
        override suspend fun applyChanges(changes: ByteArray) = Unit
    }

    private fun storeOver(
        database: KitsDatabase,
        refreshNew: suspend (TestSource) -> Unit = { it.refresh() },
        create: suspend (Document, suspend (ByteArray) -> Unit, () -> Unit) -> TestSource? =
            { document, persist, onLocalChange -> TestSource(document, persist, onLocalChange) },
    ) = HomeDocumentStore(
        database,
        feature = "test-feature",
        nowMillis = { 42L },
        refreshNew = refreshNew,
        create = { _, document, persist, onLocalChange -> create(document, persist, onLocalChange) },
    )

    @Test
    fun opensOnceAndReturnsTheSameInstance() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        var opened = 0
        val sut = storeOver(database) { d, p, c -> opened++; TestSource(d, p, c) }

        // Act
        val first = assertNotNull(sut.forHome("home-1"))
        val second = sut.forHome("home-1")

        // Assert
        assertSame(first, second)
        assertEquals(1, opened)
    }

    @Test
    fun returnsNullForAMissingHomeAndEvictsAfterTheHomeIsDeleted() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        val sut = storeOver(database)
        val beforeHomeExists = sut.forHome("home-1")
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val before = assertNotNull(sut.forHome("home-1"))

        // Act
        database.homeQueries.deleteById("home-1")
        val whileDeleted = sut.forHome("home-1")
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val after = sut.forHome("home-1")

        // Assert
        assertNull(beforeHomeExists)
        assertNull(whileDeleted)
        assertNotNull(after)
        assertNotSame(before, after)
    }

    @Test
    fun loadsAStoredDocumentWithoutRefreshingIt() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val seeded = Document()
        val tx = seeded.startTransaction()
        tx.set(ObjectId.ROOT, "stored-key", "v")
        tx.commit()
        database.automergeDocumentQueries.upsert(homeDocumentId("home-1", "test-feature"), "home-1", "test-feature", seeded.save(), 1L)
        var refreshed = false
        val sut = storeOver(database, refreshNew = { refreshed = true })

        // Act
        val source = assertNotNull(sut.forHome("home-1"))

        // Assert
        assertEquals(true, source.has("stored-key"))
        assertEquals(false, refreshed)
    }

    @Test
    fun storesANewDocumentStraightAway() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sut = storeOver(database)

        // Act
        sut.forHome("home-1")

        // Assert
        val stored = assertNotNull(database.automergeDocumentQueries.selectById("home-1:test-feature").awaitAsOneOrNull())
        assertEquals("home-1", stored.homeId)
        assertEquals("test-feature", stored.feature)
        assertEquals(42L, stored.updatedAt)
    }

    @Test
    fun localChangesEmitsTheHomeIdOnAnEditButNotOnAnIncomingSync() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sut = storeOver(database)
        val source = assertNotNull(sut.forHome("home-1"))
        val emitted = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { sut.localChanges.collect { emitted += it } }

        // Act
        source.receiveSync(SyncState(), ByteArray(0))
        source.applyChanges(ByteArray(0))
        yield()
        val afterIncoming = emitted.toList()
        source.edit("k")
        while (emitted.isEmpty()) yield()
        job.cancel()

        // Assert
        assertEquals(emptyList(), afterIncoming)
        assertEquals(listOf("home-1"), emitted)
    }

    @Test
    fun createReturningNullYieldsNullAndDoesNotCache() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        var ready = false
        var attempts = 0
        val sut = storeOver(database) { d, p, c -> attempts++; if (ready) TestSource(d, p, c) else null }

        // Act
        val notYet = sut.forHome("home-1")
        ready = true
        val now = sut.forHome("home-1")

        // Assert
        assertNull(notYet)
        assertNotNull(now)
        assertEquals(2, attempts)
    }
}
