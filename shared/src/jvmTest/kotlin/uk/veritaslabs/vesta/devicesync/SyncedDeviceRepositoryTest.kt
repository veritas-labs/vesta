package uk.veritaslabs.vesta.devicesync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.SyncedDevice

class SyncedDeviceRepositoryTest {

    private var clock = 0L

    private fun repository() = SyncedDeviceRepository(openInMemoryDatabase()) { clock++ }

    @Test
    fun emitsEmptyWhenThereAreNoDevices() = runBlocking {
        // Arrange
        val repository = repository()

        // Act
        val devices = repository.devices.first()

        // Assert
        assertEquals(emptyList(), devices)
    }

    @Test
    fun emitsAddedDevicesOldestFirst() = runBlocking {
        // Arrange
        val repository = repository()

        // Act
        repository.add("Pixel", "agent-b")
        repository.add("Desktop", "agent-a")
        val devices = repository.devices.first()

        // Assert
        assertEquals(listOf(SyncedDevice("Pixel", "agent-b"), SyncedDevice("Desktop", "agent-a")), devices)
    }

    @Test
    fun addingAnExistingAgentRenamesItWithoutDuplicating() = runBlocking {
        // Arrange
        val repository = repository()
        repository.add("Pixel", "agent-a")

        // Act
        repository.add("Mark's Pixel", "agent-a")
        val devices = repository.devices.first()

        // Assert
        assertEquals(listOf(SyncedDevice("Mark's Pixel", "agent-a")), devices)
    }

    @Test
    fun removeForgetsOnlyThatDevice() = runBlocking {
        // Arrange
        val repository = repository()
        repository.add("Pixel", "agent-a")
        repository.add("Desktop", "agent-b")

        // Act
        repository.remove("agent-a")
        val devices = repository.devices.first()

        // Assert
        assertEquals(listOf(SyncedDevice("Desktop", "agent-b")), devices)
    }

    @Test
    fun removingAnUnknownDeviceIsANoOp() = runBlocking {
        // Arrange
        val repository = repository()
        repository.add("Pixel", "agent-a")

        // Act
        repository.remove("nope")
        val devices = repository.devices.first()

        // Assert
        assertEquals(listOf(SyncedDevice("Pixel", "agent-a")), devices)
    }
}
