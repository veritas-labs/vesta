package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.automerge.Document
import org.automerge.ObjectId

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

    @Test
    fun severalHubsAreRecordedBesideEachOther() {
        // Arrange
        val doc = Document()

        // Act
        val first = addRosterHub(doc, "hub-1")
        val second = addRosterHub(doc, "hub-2")

        // Assert
        assertTrue(first)
        assertTrue(second)
        assertEquals(setOf("hub-1", "hub-2"), doc.readRosterHubs())
    }

    @Test
    fun addingAHubAgainChangesNothing() {
        // Arrange
        val doc = Document()
        addRosterHub(doc, "hub-1")

        // Act
        val changed = addRosterHub(doc, "hub-1")

        // Assert
        assertFalse(changed)
        assertEquals(setOf("hub-1"), doc.readRosterHubs())
    }

    @Test
    fun removingAHubLeavesTheOthers() {
        // Arrange
        val doc = Document()
        addRosterHub(doc, "hub-1")
        addRosterHub(doc, "hub-2")

        // Act
        val changed = removeRosterHub(doc, "hub-1")
        val again = removeRosterHub(doc, "hub-1")

        // Assert
        assertTrue(changed)
        assertFalse(again)
        assertEquals(setOf("hub-2"), doc.readRosterHubs())
    }

    @Test
    fun aRosterWithNoHubsReadsNone() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)

        // Act
        val hubs = doc.readRosterHubs()

        // Assert
        assertTrue(hubs.isEmpty())
    }

    @Test
    fun theOlderSingleHubValueIsReadAndRemoved() {
        // Arrange
        val doc = Document()
        doc.startTransaction().also {
            it.set(ObjectId.ROOT, "hub", "old-hub")
            it.commit()
        }
        addRosterHub(doc, "hub-2")

        // Act
        val before = doc.readRosterHubs()
        removeRosterHub(doc, "old-hub")

        // Assert
        assertEquals(setOf("old-hub", "hub-2"), before)
        assertEquals(setOf("hub-2"), doc.readRosterHubs())
    }

    @Test
    fun hubsAddedOnTwoDevicesAtOnceMergeToBoth() {
        // Arrange
        val first = Document()
        addRosterHub(first, "hub-0")
        val second = first.fork()

        // Act
        addRosterHub(first, "hub-1")
        addRosterHub(second, "hub-2")
        first.merge(second)

        // Assert
        assertEquals(setOf("hub-0", "hub-1", "hub-2"), first.readRosterHubs())
    }

    @Test
    fun aHubMayAlsoBeAMembersDevice() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(doc, "agent-a", "m1")

        // Act
        addRosterHub(doc, "agent-a")

        // Assert
        assertEquals(setOf("agent-a"), doc.readRosterHubs())
        assertEquals(setOf("agent-a"), doc.readRoster().single().devices)
    }

    @Test
    fun aDeviceThatClaimsItselfAgainAfterSeeingItsReleaseIsBackOnEveryReplica() {
        // Arrange: A releases the device; the device, which has merged that release, claims itself again
        val a = Document()
        putRosterMember(a, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(a, "agent-lost", "m1")
        releaseRosterDevice(a, "agent-lost", "m1")
        val device = a.fork()

        // Act
        claimRosterDevice(device, "agent-lost", "m1")
        a.merge(device)

        // Assert: a release alone does not keep a device out (docs/plans/unpaired-device-gap-plan.md, slice 0a)
        assertEquals(setOf("agent-lost"), a.readRoster().single().devices)
    }

    @Test
    fun aSameValuePutThatIsConcurrentWithAReleaseDoesNotKeepTheDevice() {
        // Arrange: both forks start with the claim; one deletes it while the other writes the same value again
        val a = Document()
        putRosterMember(a, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(a, "agent-lost", "m1")
        val device = a.fork()

        // Act
        releaseRosterDevice(a, "agent-lost", "m1")
        device.startTransaction().also {
            it.set(ObjectId.ROOT, "device:agent-lost", "m1")
            it.commit()
        }
        a.merge(device)

        // Assert: the delete wins over a put of the value that was already there; only a put made after seeing the release brings it back
        assertEquals(emptySet(), a.readRoster().single().devices)
    }

    @Test
    fun aClaimThatTheDeviceDidNotNeedToWriteAgainStaysReleased() {
        // Arrange: the device still has its claim and so writes nothing
        val a = Document()
        putRosterMember(a, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(a, "agent-lost", "m1")
        val device = a.fork()

        // Act
        releaseRosterDevice(a, "agent-lost", "m1")
        val wrote = claimRosterDevice(device, "agent-lost", "m1")
        a.merge(device)

        // Assert
        assertFalse(wrote)
        assertEquals(emptySet(), a.readRoster().single().devices)
    }

    @Test
    fun aDeviceMarkedGoneIsLeftOutOfItsMembersDevicesButKeepsItsClaim() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(doc, "agent-a", "m1")
        claimRosterDevice(doc, "agent-lost", "m1")

        // Act
        val changed = markRosterGone(doc, "agent-lost", "m1")

        // Assert
        assertTrue(changed)
        assertEquals(setOf("agent-a"), doc.readRoster().single().devices)
        assertEquals(mapOf("agent-lost" to "m1"), doc.readRosterGone())
        assertEquals(1, doc.readRoster().size)
    }

    @Test
    fun aMemberCanOnlyMarkADeviceTheRosterSaysIsTheirs() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        putRosterMember(doc, "m2", "Sam", null, "member", 101L)
        claimRosterDevice(doc, "agent-sam", "m2")

        // Act
        val byAnother = markRosterGone(doc, "agent-sam", "m1")
        val unknown = markRosterGone(doc, "agent-nobody", "m1")

        // Assert
        assertFalse(byAnother)
        assertFalse(unknown)
        assertEquals(emptyMap(), doc.readRosterGone())
        assertEquals(setOf("agent-sam"), doc.readRoster().first { it.memberId == "m2" }.devices)
    }

    @Test
    fun aTombstoneWrittenForAnotherMembersDeviceIsIgnoredWhenRead() {
        // Arrange: written raw, as a device that skipped markRosterGone would
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        putRosterMember(doc, "m2", "Sam", null, "member", 101L)
        claimRosterDevice(doc, "agent-sam", "m2")

        // Act
        doc.startTransaction().also {
            it.set(ObjectId.ROOT, "gone:agent-sam", "m1")
            it.commit()
        }

        // Assert
        assertEquals(setOf("agent-sam"), doc.readRoster().first { it.memberId == "m2" }.devices)
    }

    @Test
    fun markingTheSameDeviceAgainChangesNothing() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(doc, "agent-lost", "m1")
        markRosterGone(doc, "agent-lost", "m1")

        // Act
        val changed = markRosterGone(doc, "agent-lost", "m1")

        // Assert
        assertFalse(changed)
    }

    @Test
    fun aMarkedDeviceThatClaimsItselfAgainStaysOut() {
        // Arrange: the lost device merges the document and writes its claim, which is the same value
        val a = Document()
        putRosterMember(a, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(a, "agent-lost", "m1")
        markRosterGone(a, "agent-lost", "m1")
        val device = a.fork()
        device.startTransaction().also {
            it.delete(ObjectId.ROOT, "device:agent-lost")
            it.commit()
        }
        claimRosterDevice(device, "agent-lost", "m1")

        // Act
        a.merge(device)

        // Assert: its claim is back, and the tombstone keeps it out
        assertEquals(emptySet(), a.readRoster().single().devices)
        assertEquals(setOf("agent-lost"), a.readRosterGone().keys)
    }

    @Test
    fun clearingTheMarkBringsTheDeviceBack() {
        // Arrange
        val doc = Document()
        putRosterMember(doc, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(doc, "agent-lost", "m1")
        markRosterGone(doc, "agent-lost", "m1")

        // Act
        val changed = clearRosterGone(doc, "agent-lost")
        val again = clearRosterGone(doc, "agent-lost")

        // Assert
        assertTrue(changed)
        assertFalse(again)
        assertEquals(setOf("agent-lost"), doc.readRoster().single().devices)
    }

    @Test
    fun devicesMarkedOnTwoReplicasAtOnceAreBothOut() {
        // Arrange
        val first = Document()
        putRosterMember(first, "m1", "Mark", null, "admin", 100L)
        claimRosterDevice(first, "agent-a", "m1")
        claimRosterDevice(first, "agent-b", "m1")
        claimRosterDevice(first, "agent-c", "m1")
        val second = first.fork()

        // Act
        markRosterGone(first, "agent-a", "m1")
        markRosterGone(second, "agent-b", "m1")
        first.merge(second)

        // Assert
        assertEquals(setOf("agent-c"), first.readRoster().single().devices)
        assertEquals(setOf("agent-a", "agent-b"), first.readRosterGone().keys)
    }
}
