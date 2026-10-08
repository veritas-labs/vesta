package uk.veritaslabs.vesta.devicesync

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.x0x.X0xFileEvent

class FakeX0xNetworkFilesTest {

    private val network = FakeX0xNetwork()
    private val a = network.node("a")
    private val b = network.node("b")

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun source(bytes: ByteArray): File = Files.createTempFile("fake-src", ".webp").toFile().also { it.writeBytes(bytes); it.deleteOnExit() }

    @Test
    fun `an offer reaches an online node, and accepting lands the verified file and announces it`() = runTest {
        // Arrange
        val bytes = ByteArray(100) { it.toByte() }
        val events = mutableListOf<X0xFileEvent>()
        b.fileEvents({ events += it })

        // Act
        val id = a.sendFile("b", "h.webp", bytes.size.toLong(), sha(bytes), source(bytes).path)
        val pendingBeforeAccept = b.getFileTransfer(id)!!.state
        b.acceptFile(id)

        // Assert
        val complete = events[1] as X0xFileEvent.Complete
        assertEquals(X0xFileEvent.Offer(id, "h.webp", 100, "a"), events[0])
        assertEquals("Pending", pendingBeforeAccept)
        assertEquals("Complete", b.getFileTransfer(id)!!.state)
        assertEquals("Complete", a.getFileTransfer(id)!!.state)
        assertEquals("${id.take(8)}_h.webp", File(complete.path).name)
        assertEquals(b.transfersDir, File(complete.path).parentFile)
        assertTrue(bytes.contentEquals(File(complete.path).readBytes()))
        assertEquals(sha(bytes), complete.sha256)
    }

    @Test
    fun `an offer to an offline node is never delivered and stays pending`() = runTest {
        // Arrange
        network.offline += "b"
        val events = mutableListOf<X0xFileEvent>()
        b.fileEvents({ events += it })
        val bytes = byteArrayOf(1, 2, 3)

        // Act
        val id = a.sendFile("b", "h.webp", 3, sha(bytes), source(bytes).path)
        network.comeOnline("b")

        // Assert
        assertTrue(events.isEmpty())
        assertEquals("Pending", a.getFileTransfer(id)!!.state)
    }

    @Test
    fun `a checksum mismatch fails the receiver with no file and no event while the sender shows complete`() = runTest {
        // Arrange
        val events = mutableListOf<X0xFileEvent>()
        b.fileEvents({ events += it })
        val id = a.sendFile("b", "h.webp", 3, "00".repeat(32), source(byteArrayOf(1, 2, 3)).path)

        // Act
        b.acceptFile(id)

        // Assert
        val record = b.getFileTransfer(id)!!
        assertEquals("Failed", record.state)
        assertTrue(record.error!!.startsWith("SHA-256 mismatch"))
        assertEquals("Complete", a.getFileTransfer(id)!!.state)
        assertEquals(1, events.size)
        assertTrue(b.transfersDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `rejecting an offer rejects it on both ends and leaves no file`() = runTest {
        // Arrange
        val id = a.sendFile("b", "h.webp", 3, "x", source(byteArrayOf(1, 2, 3)).path)

        // Act
        b.rejectFile(id)

        // Assert
        assertEquals("Rejected", a.getFileTransfer(id)!!.state)
        assertEquals("Rejected", b.getFileTransfer(id)!!.state)
        assertFalse(b.transfersDir.listFiles()!!.any())
    }

    @Test
    fun `a node lists only its own transfers`() = runTest {
        // Arrange
        val c = network.node("c")
        val bytes = byteArrayOf(9)
        val id = a.sendFile("b", "h.webp", 1, sha(bytes), source(bytes).path)

        // Act
        val listed = listOf(a, b, c).map { it.listFileTransfers().map { t -> t.transferId } }

        // Assert
        assertEquals(listOf(listOf(id), listOf(id), emptyList()), listed)
    }
}
