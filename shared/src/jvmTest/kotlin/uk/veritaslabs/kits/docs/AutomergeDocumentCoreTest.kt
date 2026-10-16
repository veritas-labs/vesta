@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.kits.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.SyncState

class AutomergeDocumentCoreTest {

    private class Probe(val document: Document = Document()) {
        var published = 0
        var persisted = 0
        var changes = 0
        val core = AutomergeDocumentCore(
            document = document,
            publish = { published++ },
            persist = { persisted++ },
            onLocalChange = { changes++ },
        )
    }

    private fun Document.put(key: String, value: String) {
        val tx = startTransaction()
        tx.set(ObjectId.ROOT, key, value)
        tx.commit()
    }

    private fun Document.keys(): Set<String> = mapEntries(ObjectId.ROOT).orElse(emptyArray()).map { it.key }.toSet()

    /** Runs a sync exchange between two cores until neither has anything more to say. */
    private suspend fun converge(a: AutomergeDocumentCore, b: AutomergeDocumentCore) {
        val sa = SyncState()
        val sb = SyncState()
        repeat(20) {
            val ma = a.generateSync(sa)?.also { b.receiveSync(sb, it) }
            val mb = b.generateSync(sb)?.also { a.receiveSync(sa, it) }
            if (ma == null && mb == null) return
        }
    }

    @Test
    fun anEditPublishesPersistsAndFiresOnLocalChangeOnce() = runBlocking {
        // Arrange
        val probe = Probe()

        // Act
        val result = probe.core.edit { put("a", "1"); 42 }

        // Assert
        assertEquals(42, result)
        assertEquals(1, probe.published)
        assertEquals(1, probe.persisted)
        assertEquals(1, probe.changes)
        assertEquals(setOf("a"), probe.document.keys())
    }

    @Test
    fun editIfChangedWithNoChangeDoesNothing() = runBlocking {
        // Arrange
        val probe = Probe()

        // Act
        val changed = probe.core.editIfChanged { false }

        // Assert
        assertFalse(changed)
        assertEquals(0, probe.published)
        assertEquals(0, probe.persisted)
        assertEquals(0, probe.changes)
    }

    @Test
    fun editIfChangedThatChangedPublishesPersistsAndFires() = runBlocking {
        // Arrange
        val probe = Probe()

        // Act
        val changed = probe.core.editIfChanged { put("a", "1"); true }

        // Assert
        assertTrue(changed)
        assertEquals(listOf(1, 1, 1), listOf(probe.published, probe.persisted, probe.changes))
    }

    @Test
    fun concurrentEditsDoNotInterleave() = runBlocking {
        // Arrange
        val probe = Probe()
        val log = mutableListOf<String>()

        // Act
        (1..5).map { n ->
            async(kotlinx.coroutines.Dispatchers.Default) {
                probe.core.edit {
                    log += "start $n"
                    Thread.sleep(10)
                    put("k$n", "v")
                    log += "end $n"
                }
            }
        }.awaitAll()

        // Assert
        log.chunked(2).forEach { (start, end) -> assertEquals(start.removePrefix("start "), end.removePrefix("end ")) }
        assertEquals(10, log.size)
        assertEquals(5, probe.document.keys().size)
        assertEquals(5, probe.persisted)
    }

    @Test
    fun anIncomingSyncMessageThatChangesNothingDoesNotPersist() = runBlocking {
        // Arrange
        val a = Probe()
        val b = Probe()
        a.core.edit { put("a", "1") }
        converge(a.core, b.core)
        val publishedBefore = b.published
        val persistedBefore = b.persisted

        // Act
        converge(a.core, b.core)

        // Assert
        assertEquals(publishedBefore, b.published)
        assertEquals(persistedBefore, b.persisted)
    }

    @Test
    fun receiveSyncFromASecondDocumentConverges() = runBlocking {
        // Arrange
        val a = Probe()
        val b = Probe()
        a.core.edit { put("a", "1") }
        b.core.edit { put("b", "2") }

        // Act
        converge(a.core, b.core)

        // Assert
        assertEquals(setOf("a", "b"), a.document.keys())
        assertEquals(setOf("a", "b"), b.document.keys())
        assertEquals(1, b.published - 1)
        assertTrue(b.persisted >= 2)
        assertEquals(1, b.changes)
    }

    @Test
    fun applyChangesIsIdempotent() = runBlocking {
        // Arrange
        val a = Probe()
        val b = Probe()
        a.core.edit { put("a", "1") }
        val changes = a.core.changesSince(ByteArray(0))
        b.core.applyChanges(changes)
        val persistedAfterFirst = b.persisted

        // Act
        b.core.applyChanges(changes)

        // Assert
        assertEquals(setOf("a"), b.document.keys())
        assertEquals(1, persistedAfterFirst)
        assertEquals(1, b.persisted)
        assertEquals(1, b.published)
        assertEquals(a.core.heads().toList(), b.core.heads().toList())
    }

    @Test
    fun refreshPublishesAndPersists() = runBlocking {
        // Arrange
        val probe = Probe()

        // Act
        probe.core.refresh()

        // Assert
        assertEquals(1, probe.published)
        assertEquals(1, probe.persisted)
        assertEquals(0, probe.changes)
    }
}
