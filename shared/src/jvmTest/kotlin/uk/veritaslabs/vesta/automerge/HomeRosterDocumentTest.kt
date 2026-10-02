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

    @Test
    fun releasingADeviceRemovesItsClaimButKeepsTheMember() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(doc, "agent-a", "m1")
        claimRosterDevice(doc, "agent-b", "m1")

        // Act
        val changed = releaseRosterDevice(doc, "agent-b", "m1")

        // Assert
        assertTrue(changed)
        val member = doc.readRoster().single()
        assertEquals(setOf("agent-a"), member.devices)
        assertFalse(member.removed)
    }

    @Test
    fun releasingADeviceThatActsForSomeoneElseDoesNothing() {
        // Arrange
        val doc = Document()
        claimRosterDevice(doc, "agent-b", "m2")

        // Act
        val changed = releaseRosterDevice(doc, "agent-b", "m1")

        // Assert
        assertFalse(changed)
    }

    @Test
    fun settingARoleOverwritesTheOneThere() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Sam", null, "member", 100L)

        // Act
        val changed = setRosterRole(doc, "m1", "admin")
        val unchanged = putRosterMember(doc, "m1", "Sam", null, "member", 100L) // a device republishing its own entry

        // Assert
        assertTrue(changed)
        assertFalse(unchanged)
        assertEquals("admin", doc.readRoster().single().role)
    }

    @Test
    fun settingTheRoleOfAnUnknownMemberDoesNothing() {
        // Arrange
        val doc = Document()

        // Act
        val changed = setRosterRole(doc, "nobody", "admin")

        // Assert
        assertFalse(changed)
        assertEquals(emptyList(), doc.readRoster())
    }

    @Test
    fun aWrittenInviteIsRead() {
        // Arrange
        val doc = Document()

        // Act
        val changed = putRosterInvite(doc, "hash-1", createdBy = "m1", expiresAt = 5_000L, maxUses = 1)

        // Assert
        assertTrue(changed)
        assertEquals(
            listOf(RosterInvite("hash-1", "m1", 5_000L, 1, revoked = false, usedBy = emptySet())),
            doc.readInvites(),
        )
    }

    @Test
    fun anInviteIsNotMistakenForAMember() {
        // Arrange
        val doc = Document()

        // Act
        putRosterInvite(doc, "hash-1", "m1", 5_000L, 1)

        // Assert
        assertEquals(emptyList(), doc.readRoster())
    }

    @Test
    fun writingAnExistingInviteAgainChangesNothing() {
        // Arrange
        val doc = Document()
        putRosterInvite(doc, "hash-1", "m1", 5_000L, 1)

        // Act
        val changed = putRosterInvite(doc, "hash-1", "m2", 9_000L, 5)

        // Assert
        assertFalse(changed)
        assertEquals("m1", doc.readInvites().single().createdBy)
    }

    @Test
    fun revokingAnInviteMarksItRevokedOnce() {
        // Arrange
        val doc = Document()
        putRosterInvite(doc, "hash-1", "m1", 5_000L, 1)

        // Act
        val changed = revokeRosterInvite(doc, "hash-1")
        val again = revokeRosterInvite(doc, "hash-1")

        // Assert
        assertTrue(changed)
        assertFalse(again)
        assertTrue(doc.readInvites().single().revoked)
    }

    @Test
    fun revokingOrUsingAnUnknownInviteDoesNothing() {
        // Arrange
        val doc = Document()

        // Act
        val revoked = revokeRosterInvite(doc, "nope")
        val used = useRosterInvite(doc, "nope", "m2")

        // Assert
        assertFalse(revoked)
        assertFalse(used)
        assertEquals(emptyList(), doc.readInvites())
    }

    @Test
    fun twoAdminDevicesSpendingAnInviteConcurrentlyBothCount() {
        // Arrange
        val first = Document()
        putRosterInvite(first, "hash-1", "m1", 5_000L, 2)
        val second = first.fork()

        // Act
        useRosterInvite(first, "hash-1", "sam")
        useRosterInvite(second, "hash-1", "alex")
        first.merge(second)

        // Assert
        assertEquals(setOf("sam", "alex"), first.readInvites().single().usedBy)
    }

    @Test
    fun anInviteIsValidUntilItExpires() {
        // Arrange
        val invite = RosterInvite("h", "m1", expiresAt = 100L, maxUses = 1, revoked = false, usedBy = emptySet())

        // Act
        val before = invite.statusFor("sam", 99L)
        val at = invite.statusFor("sam", 100L)

        // Assert
        assertEquals(InviteStatus.Valid, before)
        assertEquals(InviteStatus.Expired, at)
    }

    @Test
    fun aRevokedInviteIsRevokedEvenWhenExpired() {
        // Arrange
        val invite = RosterInvite("h", "m1", 100L, 1, revoked = true, usedBy = emptySet())

        // Act
        val status = invite.statusFor("sam", 500L)

        // Assert
        assertEquals(InviteStatus.Revoked, status)
    }

    @Test
    fun aSpentInviteIsUsedForOthersButStillValidForWhoSpentIt() {
        // Arrange
        val invite = RosterInvite("h", "m1", 100L, maxUses = 1, revoked = false, usedBy = setOf("sam"))

        // Act
        val other = invite.statusFor("alex", 50L)
        val same = invite.statusFor("sam", 50L)

        // Assert
        assertEquals(InviteStatus.Used, other)
        assertEquals(InviteStatus.Valid, same)
    }

    @Test
    fun anInviteSecretIsHashedAndTheHashIsStable() {
        // Arrange
        val secret = newInviteSecret()

        // Act
        val hash = inviteSecretHash(secret)

        // Assert
        assertEquals(hash, inviteSecretHash(secret))
        assertEquals(64, hash.length)
        assertTrue(secret !in hash)
        assertTrue(newInviteSecret() != secret)
    }
}
