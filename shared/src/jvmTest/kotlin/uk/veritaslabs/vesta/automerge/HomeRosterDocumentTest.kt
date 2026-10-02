package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.automerge.Document

class HomeRosterDocumentTest {

    @Test
    fun aWrittenMemberAndDeviceAreRead() {
        // Arrange
        val doc = Document()

        // Act
        putRosterMember(doc, "m1", "Mark", "avatar", "admin", joinedAt = 100L)
        claimRosterDevice(doc, "agent-a", "m1")

        // Assert
        assertEquals(
            listOf(RosterMember("m1", "Mark", "avatar", "admin", 100L, removed = false, devices = setOf("agent-a"))),
            doc.readRoster(),
        )
    }

    @Test
    fun writingTheSameMemberAgainChangesNothing() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)

        // Act
        val changed = putRosterMember(doc, "m1", "Mark", null, "member", 999L)

        // Assert
        assertFalse(changed)
        assertEquals("admin", doc.readRoster().single().role)
    }

    @Test
    fun twoDevicesWritingTheSameMemberConcurrentlyKeepBothDevices() {
        // Arrange
        val first = Document()
        putRosterMember(first, "m1", "Mark", null, "admin", 100L)
        val second = first.fork()

        // Act
        claimRosterDevice(first, "agent-a", "m1")
        putRosterMember(second, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(second, "agent-b", "m1")
        first.merge(second)

        // Assert
        val member = first.readRoster().single()
        assertEquals(setOf("agent-a", "agent-b"), member.devices)
        assertEquals("Mark", member.name)
    }

    @Test
    fun aRetiredMemberIsMarkedRemoved() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "old", "Tablet", null, "admin", 100L)

        // Act
        val changed = retireRosterMember(doc, "old")
        val again = retireRosterMember(doc, "old")

        // Assert
        assertTrue(changed)
        assertFalse(again)
        assertTrue(doc.readRoster().single().removed)
    }

    @Test
    fun retiringAnUnknownMemberDoesNothing() {
        // Arrange
        val doc = Document()

        // Act
        val changed = retireRosterMember(doc, "nobody")

        // Assert
        assertFalse(changed)
        assertEquals(emptyList(), doc.readRoster())
    }
}
