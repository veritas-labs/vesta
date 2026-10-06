package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.automerge.Document
import org.automerge.ObjectId
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.decodeHeads
import uk.veritaslabs.vesta.mailbox.encodeHeads

/** What the mailbox relies on from Automerge's `encodeChangesSince` / `applyEncodedChanges` (ADR-020 plan, slice 3). */
class EncodedChangesTest {

    private fun Document.put(key: String, value: String) {
        val tx = startTransaction()
        tx.set(ObjectId.ROOT, key, value)
        tx.commit()
    }

    private fun Document.keys(): Set<String> = mapEntries(ObjectId.ROOT).orElse(emptyArray()).map { it.key }.toSet()

    @Test
    fun savedHeadsComeBackAsHeadsTheLibraryAccepts() {
        // Arrange
        val doc = Document()
        doc.put("a", "1")
        val saved = encodeHeads(doc.heads)
        doc.put("b", "2")

        // Act
        val changes = doc.changesSince(saved)

        // Assert
        assertContentEquals(saved, encodeHeads(decodeHeads(saved)))
        val reader = Document().also { it.applyEncodedChanges(doc.changesSince(ByteArray(0))) }
        assertEquals(setOf("a", "b"), reader.keys())
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.size < doc.changesSince(ByteArray(0)).size)
    }

    @Test
    fun changesSinceTheLastDepositCarryOnlyTheNewWork() {
        // Arrange
        val writer = Document()
        writer.put("a", "1")
        val reader = Document().also { it.applyEncodedChanges(writer.changesSince(ByteArray(0))) }
        val deposited = encodeHeads(writer.heads)
        writer.put("b", "2")

        // Act
        reader.applyEncodedChanges(writer.changesSince(deposited))

        // Assert
        assertEquals(setOf("a", "b"), reader.keys())
        assertContentEquals(encodeHeads(writer.heads), encodeHeads(reader.heads))
    }

    @Test
    fun applyingTheSameChangesTwiceChangesNothing() {
        // Arrange
        val writer = Document()
        writer.put("a", "1")
        val changes = writer.changesSince(ByteArray(0))
        val reader = Document().also { it.applyEncodedChanges(changes) }
        val before = encodeHeads(reader.heads)

        // Act
        reader.applyEncodedChanges(changes)

        // Assert
        assertContentEquals(before, encodeHeads(reader.heads))
        assertEquals(setOf("a"), reader.keys())
    }

    @Test
    fun changesWhoseEarlierOnesHaveNotArrivedAreKeptUntilTheyDo() {
        // Arrange
        val writer = Document()
        writer.put("a", "1")
        val first = writer.changesSince(ByteArray(0))
        val afterFirst = encodeHeads(writer.heads)
        writer.put("b", "2")
        val second = writer.changesSince(afterFirst)
        val reader = Document()

        // Act
        val outcome = runCatching { reader.applyEncodedChanges(second) }
        val keysBeforeFirst = reader.keys()
        reader.applyEncodedChanges(first)

        // Assert
        // Automerge queues it rather than throwing, so the mailbox reader need not hold entries back for ordering.
        assertNull(outcome.exceptionOrNull())
        assertEquals(emptySet(), keysBeforeFirst)
        assertEquals(setOf("a", "b"), reader.keys())
    }
}
