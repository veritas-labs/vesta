package uk.veritaslabs.vesta.devicesync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.x0x.X0xApiException

/** Pins the fake mesh's group stores to what ADR-020 Spikes 1, 4 and 6 saw on real daemons. */
class FakeGroupStoreTest {

    private val network = FakeX0xNetwork()
    private val phoneA = network.node("phone-a")
    private val phoneB = network.node("phone-b")
    private val hub = network.node("hub")

    /** A creates the group, B and the hub join, the hub is promoted, and all three open `mailbox`. */
    private suspend fun household(): Triple<String, String, String> {
        val groupId = phoneA.createGroup("Home")
        val invite = phoneA.createGroupInvite(groupId)
        phoneB.joinGroup(invite)
        hub.joinGroup(invite)
        phoneA.setGroupMemberRole(groupId, "hub", "admin")
        val storeId = phoneA.openGroupStore(groupId, "mailbox")
        assertEquals(storeId, phoneB.openGroupStore(groupId, "mailbox"))
        assertEquals(storeId, hub.openGroupStore(groupId, "mailbox"))
        return Triple(groupId, "mailbox", storeId)
    }

    @Test
    fun `a reader catches up from the hub while the writer is offline`() = runBlocking {
        // Arrange
        val (_, _, storeId) = household()
        network.offline += "phone-b"

        // Act
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(1, 2, 3))
        network.offline += "phone-a"
        network.comeOnline("phone-b")

        // Assert
        assertContentEquals(byteArrayOf(1, 2, 3), phoneB.getValue(storeId, "phone-a/0001"))
        assertEquals(listOf("phone-a/0001"), phoneB.listStoreKeys(storeId).map { it.key })
    }

    @Test
    fun `an offline writer's entries reach the others when it comes back`() = runBlocking {
        // Arrange
        val (_, _, storeId) = household()
        network.offline += "phone-a"

        // Act
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(9))
        val beforeReturn = hub.getValue(storeId, "phone-a/0001")
        network.comeOnline("phone-a")

        // Assert
        assertNull(beforeReturn)
        assertContentEquals(byteArrayOf(9), hub.getValue(storeId, "phone-a/0001"))
        assertContentEquals(byteArrayOf(9), phoneB.getValue(storeId, "phone-a/0001"))
    }

    @Test
    fun `a member who joins later reads the history with the writer offline`() = runBlocking {
        // Arrange
        val (groupId, name, storeId) = household()
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(7))
        network.offline += "phone-a"
        val phoneC = network.node("phone-c")
        phoneC.joinGroup(hub.createGroupInvite(groupId))

        // Act
        phoneC.openGroupStore(groupId, name)

        // Assert
        assertContentEquals(byteArrayOf(7), phoneC.getValue(storeId, "phone-a/0001"))
    }

    @Test
    fun `a deletion by one member reaches the others`() = runBlocking {
        // Arrange
        val (_, _, storeId) = household()
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(1))

        // Act
        phoneB.deleteValue(storeId, "phone-a/0001")

        // Assert
        assertNull(hub.getValue(storeId, "phone-a/0001"))
        assertEquals(emptyList(), phoneA.listStoreKeys(storeId))
    }

    @Test
    fun `a removed member can no longer read or write`() = runBlocking {
        // Arrange
        val (groupId, _, storeId) = household()
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(1))

        // Act
        phoneA.removeGroupMember(groupId, "phone-b")

        // Assert
        assertFailsWith<X0xApiException> { phoneB.getValue(storeId, "phone-a/0001") }
        assertFailsWith<X0xApiException> { phoneB.putValue(storeId, "phone-b/0001", byteArrayOf(2)) }
    }

    @Test
    fun `a value over 64 KiB is refused and 64 KiB is accepted`() = runBlocking {
        // Arrange
        val (_, _, storeId) = household()

        // Act
        phoneA.putValue(storeId, "ok", ByteArray(65_536))
        val refused = assertFailsWith<X0xApiException> { phoneA.putValue(storeId, "big", ByteArray(65_537)) }

        // Assert
        assertEquals(413, refused.status)
        assertNull(hub.getValue(storeId, "big"))
    }

    @Test
    fun `opening a store is idempotent and only for members`() = runBlocking {
        // Arrange
        val groupId = phoneA.createGroup("Home")

        // Act
        val first = phoneA.openGroupStore(groupId, "mailbox")
        phoneA.putValue(first, "k", byteArrayOf(1))
        val second = phoneA.openGroupStore(groupId, "mailbox")

        // Assert
        assertEquals(first, second)
        assertContentEquals(byteArrayOf(1), phoneA.getValue(second, "k"))
        assertFailsWith<X0xApiException> { phoneB.openGroupStore(groupId, "mailbox") }
    }

    @Test
    fun `a member who never opened the store cannot read it`() = runBlocking {
        // Arrange
        val groupId = phoneA.createGroup("Home")
        val storeId = phoneA.openGroupStore(groupId, "mailbox")
        phoneB.joinGroup(phoneA.createGroupInvite(groupId))

        // Act
        val failure = assertFailsWith<X0xApiException> { phoneB.listStoreKeys(storeId) }

        // Assert
        assertEquals(404, failure.status)
    }

    @Test
    fun `an admin hub admits a joiner while the creator is offline and the joiner reads the store`() = runBlocking {
        // Arrange
        val (groupId, name, storeId) = household()
        phoneA.putValue(storeId, "phone-a/0001", byteArrayOf(5))
        network.offline += "phone-a"
        val phoneC = network.node("phone-c")

        // Act
        phoneC.joinGroup(hub.createGroupInvite(groupId))
        phoneC.openGroupStore(groupId, name)

        // Assert
        assertContentEquals(byteArrayOf(5), phoneC.getValue(storeId, "phone-a/0001"))
    }
}
