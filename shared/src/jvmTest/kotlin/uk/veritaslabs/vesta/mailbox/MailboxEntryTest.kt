package uk.veritaslabs.vesta.mailbox

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailboxEntryTest {

    @Test
    fun anEntryDecodesToWhatWasEncoded() {
        // Arrange
        val entry = MailboxEntry("home-1:pantry", deposit = -123456, part = 2, partCount = 5, payload = byteArrayOf(1, 2, 3))

        // Act
        val decoded = MailboxEntry.decode(entry.encode())!!

        // Assert
        assertEquals("home-1:pantry", decoded.documentId)
        assertEquals(-123456, decoded.deposit)
        assertEquals(2, decoded.part)
        assertEquals(5, decoded.partCount)
        assertContentEquals(byteArrayOf(1, 2, 3), decoded.payload)
    }

    @Test
    fun smallChangesAreOneEntryAndLargeOnesAreCutIntoPartsThatFitTheValueLimit() {
        // Arrange
        val small = ByteArray(100) { it.toByte() }
        val large = ByteArray(MailboxEntry.MaxPartBytes * 2 + 17) { (it % 251).toByte() }

        // Act
        val one = MailboxEntry.split("d", small)
        val three = MailboxEntry.split("d", large)

        // Assert
        assertEquals(1, one.size)
        assertEquals(3, three.size)
        assertEquals(listOf(0, 1, 2), three.map { it.part })
        assertTrue(three.all { it.partCount == 3 && it.encode().size < 65_536 })
        assertContentEquals(large, three.map { it.payload }.fold(ByteArray(0)) { all, part -> all + part })
        assertEquals(1, three.map { it.deposit }.toSet().size)
    }

    @Test
    fun anEntryOfAnotherVersionOrDamagedIsNotDecoded() {
        // Arrange
        val good = MailboxEntry("d", 1, 0, 1, byteArrayOf(9)).encode()
        val newer = good.copyOf().also { it[0] = 2 }
        val truncated = good.copyOfRange(0, 5)
        val partPastCount = MailboxEntry("d", 1, 0, 2, byteArrayOf()).encode().also { it[it.size - 3] = 5 }

        // Act
        val decoded = listOf(newer, truncated, ByteArray(0), partPastCount).map { MailboxEntry.decode(it) }

        // Assert
        assertEquals(listOf(null, null, null, null), decoded)
    }

    @Test
    fun keysSortInWritingOrderAndOnlyMailboxKeysParse() {
        // Arrange
        val key = MailboxEntry.key("agent-1", 42)

        // Act
        val parsed = MailboxEntry.parseKey(key)

        // Assert
        assertEquals("agent-1/0000000042", key)
        assertEquals("agent-1" to 42L, parsed)
        assertTrue(MailboxEntry.key("a", 9) < MailboxEntry.key("a", 10))
        assertNull(MailboxEntry.parseKey("no-slash"))
        assertNull(MailboxEntry.parseKey("agent/12"))
        assertNull(MailboxEntry.parseKey("agent/abcdefghij"))
    }
}
