package uk.veritaslabs.vesta.syncactivity

import uk.veritaslabs.kits.sync.SyncSnapshot
import uk.veritaslabs.kits.sync.ProblemReason
import uk.veritaslabs.kits.sync.SyncVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncVerdictTest {

    private val now = 1_000_000L

    /** A healthy Home with another device that synced 10 s ago. */
    private fun snapshot(
        daemonReachable: Boolean = true,
        roundInProgress: Boolean = false,
        hasOtherDevices: Boolean = true,
        lastRoundHadFailures: Boolean = false,
        changesWaiting: Boolean = false,
        stalledMessageAgeMs: Long? = null,
        needsNewerVersion: Boolean = false,
        lastSyncedMsAgo: Long? = 10_000L,
    ) = SyncSnapshot(
        takenAt = now,
        hasOtherDevices = hasOtherDevices,
        daemonReachable = daemonReachable,
        roundInProgress = roundInProgress,
        lastRoundAt = now - 5_000,
        lastRoundHadFailures = lastRoundHadFailures,
        changesWaiting = changesWaiting,
        stalledMessageAgeMs = stalledMessageAgeMs,
        needsNewerVersion = needsNewerVersion,
        lastSyncedPeerName = "Sam's phone",
        lastSyncedMsAgo = lastSyncedMsAgo,
    )

    @Test
    fun changesFromANewerVersionOutrankUpToDate() {
        // Arrange
        val snapshot = snapshot(needsNewerVersion = true)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.NeedsNewerVersion, verdict)
    }

    @Test
    fun aProblemOutranksChangesFromANewerVersion() {
        // Arrange
        val snapshot = snapshot(needsNewerVersion = true, daemonReachable = false)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.Problem(ProblemReason.DaemonUnreachable), verdict)
    }

    @Test
    fun aRecentSyncWithNoFailuresIsUpToDate() {
        // Arrange
        val snapshot = snapshot()

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.UpToDate("Sam's phone", 10_000L), verdict)
    }

    @Test
    fun theTimeSinceTheSnapshotCountsTowardsHowLongAgoTheSyncWas() {
        // Arrange
        val snapshot = snapshot(lastSyncedMsAgo = 10_000L)

        // Act
        val soon = SyncVerdict.of(snapshot, now + 20_000)
        val later = SyncVerdict.of(snapshot, now + 51_000)

        // Assert
        assertEquals(SyncVerdict.UpToDate("Sam's phone", 30_000L), soon)
        assertEquals(SyncVerdict.WaitingForOthers, later)
    }

    @Test
    fun aSyncExactlyTwoRoundsAgoIsStillUpToDate() {
        // Arrange
        val snapshot = snapshot(lastSyncedMsAgo = SyncVerdict.UP_TO_DATE_WINDOW_MS)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.UpToDate("Sam's phone", 60_000L), verdict)
    }

    @Test
    fun aRoundWithFailuresIsNotUpToDate() {
        // Arrange
        val snapshot = snapshot(lastRoundHadFailures = true)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.WaitingForOthers, verdict)
    }

    @Test
    fun noOneHavingSyncedMeansWaitingForOthers() {
        // Arrange
        val snapshot = snapshot(lastSyncedMsAgo = null)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.WaitingForOthers, verdict)
    }

    @Test
    fun anOldSyncWithNothingUnsentIsWaitingForOthers() {
        // Arrange
        val snapshot = snapshot(lastSyncedMsAgo = 10 * 60_000L)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.WaitingForOthers, verdict)
    }

    @Test
    fun unsentChangesWithNoRecentSyncAreChangesWaiting() {
        // Arrange
        val snapshot = snapshot(lastSyncedMsAgo = null, changesWaiting = true)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.ChangesWaiting, verdict)
    }

    @Test
    fun upToDateBeatsChangesWaiting() {
        // Arrange
        val snapshot = snapshot(changesWaiting = true)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.UpToDate("Sam's phone", 10_000L), verdict)
    }

    @Test
    fun aHomeWithNoOtherDevicesIsOnlyYou() {
        // Arrange
        val snapshot = snapshot(hasOtherDevices = false, lastSyncedMsAgo = null, changesWaiting = true)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.OnlyYou, verdict)
    }

    @Test
    fun anUnreachableDaemonIsAProblem() {
        // Arrange
        val snapshot = snapshot(daemonReachable = false)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.Problem(ProblemReason.DaemonUnreachable), verdict)
    }

    @Test
    fun aMembershipMessageFailingForOverAnHourIsAProblem() {
        // Arrange
        val snapshot = snapshot(stalledMessageAgeMs = SyncVerdict.STALLED_MESSAGE_MS + 1)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.Problem(ProblemReason.MessageStalled), verdict)
    }

    @Test
    fun aMembershipMessageFailingForAnHourOrLessIsNotAProblem() {
        // Arrange
        val snapshot = snapshot(stalledMessageAgeMs = SyncVerdict.STALLED_MESSAGE_MS)

        // Act
        val verdict = SyncVerdict.of(snapshot, now)

        // Assert
        assertEquals(SyncVerdict.UpToDate("Sam's phone", 10_000L), verdict)
    }

    @Test
    fun aProblemBeatsOnlyYouAndUpToDate() {
        // Arrange
        val alone = snapshot(daemonReachable = false, hasOtherDevices = false)
        val synced = snapshot(daemonReachable = false)

        // Act
        val aloneVerdict = SyncVerdict.of(alone, now)
        val syncedVerdict = SyncVerdict.of(synced, now)

        // Assert
        assertEquals(SyncVerdict.Problem(ProblemReason.DaemonUnreachable), aloneVerdict)
        assertEquals(SyncVerdict.Problem(ProblemReason.DaemonUnreachable), syncedVerdict)
    }

    @Test
    fun aRoundInProgressShowsSyncingOverEveryOtherVerdict() {
        // Arrange
        val problem = snapshot(roundInProgress = true, daemonReachable = false)
        val alone = snapshot(roundInProgress = true, hasOtherDevices = false)
        val waiting = snapshot(roundInProgress = true, changesWaiting = true, lastSyncedMsAgo = null)

        // Act
        val verdicts = listOf(problem, alone, waiting).map { SyncVerdict.of(it, now) }

        // Assert
        assertEquals(listOf(SyncVerdict.Syncing, SyncVerdict.Syncing, SyncVerdict.Syncing), verdicts)
    }
}
