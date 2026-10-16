package uk.veritaslabs.vesta.acceptance

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.kits.sync.PeerActivity
import uk.veritaslabs.kits.sync.SyncActivityRepository
import uk.veritaslabs.vesta.syncactivity.SyncActivityViewModel
import uk.veritaslabs.kits.sync.SyncEventFilter
import uk.veritaslabs.kits.sync.SyncEventItem
import uk.veritaslabs.kits.sync.SyncEventKind
import uk.veritaslabs.kits.sync.SyncModule
import uk.veritaslabs.kits.sync.SyncSnapshot
import uk.veritaslabs.vesta.ui.screens.SyncActivityScreen
import uk.veritaslabs.vesta.ui.screens.SyncActivityTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for Sync activity (docs/adr/ADR-033-sync-activity.md; see docs/testing/acceptance-tests.md). Each test drives the real
 * [SyncActivityViewModel] and [SyncActivityScreen] over a fake [SyncActivityRepository] (the edge: the sync service), and reads what a member
 * sees. The ADR's criteria, and where each is backed:
 *
 *  1. Up to date, listing the devices with the offline one muted: here.
 *  2. Waiting for others, earlier events remaining in the log: here.
 *  3. Only you, with an Invite row: here.
 *  4. A received Tasks update reads "Tasks updated by {device}" with its time and no content: here. That repeats inside five minutes raise a
 *     count and not a new row is SyncEventRecorderTest (collapsing) and DeviceDataSyncServiceTest
 *     (aTasksEditIsLoggedAsSentOnTheEditorAndReceivedOnTheOtherMember, anUnchangedRoundIsOnlyAHandshakeAndAddsNoReceivedEvent). That no content is
 *     stored is SyncEventRecorderTest (the table has no content columns).
 *  5. A failed send appears under Problems, which shows only it: here. That the service records it, one row counting each failed round, is
 *     DeviceDataSyncServiceTest (anOfflinePeerIsLoggedAsOneUnreachableRowThatCountsEachFailedRound) and SyncActivityServiceTest
 *     (theFiltersKeepTheirKindsOnly).
 *  6. Changes waiting until a peer or hub takes them: here. How it is computed is ChangesWaitingTest and SyncActivityServiceTest
 *     (theVerdictIsChangesWaitingWhenTheyAreAwayAndNothingIsInTheMailbox).
 *  7. A restart keeps each device's last synced time: DeviceDataSyncServiceTest (theTimeAPeerLastSyncedSurvivesAFreshServiceOnTheSameDatabase)
 *     and SyncEventRecorderTest (last synced is read from memory first and from the table by a new recorder).
 *  8. Rows older than 7 days go at the next round and leaving the Home removes its rows: SyncEventRecorderTest (prune removes rows older than
 *     seven days and keeps newer ones; forgetting a home removes its rows and buffered events). The screen's "last 7 days" note is here.
 *  9. Sync now runs one round and shows progress, and is disabled with the daemon down: here. That the service starts another round is
 *     SyncActivityServiceTest (syncNowStartsAnotherRound).
 * 10. A failure inside the recorder does not stop a round: DeviceDataSyncServiceTest (aRecorderThatFailsEveryTimeLeavesTheSyncUnchanged) and
 *     SyncEventRecorderTest (a closed database does not make recording fail).
 *
 * Not covered by any automated test: a real daemon producing the same events, and the screen on a device (by-hand work, as for other sync features).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SyncActivityAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its collection run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val now = Clock.System.now().toEpochMilliseconds()

    private val sam = PeerActivity("sam", "Sam's Pixel", ownerName = "Sam", online = true, lastSyncedMsAgo = 60_000L)
    private val tom = PeerActivity("tom", "Tom's phone", ownerName = "Tom", online = false, lastSyncedMsAgo = 3 * 86_400_000L)

    private fun snapshot(
        peers: List<PeerActivity> = listOf(sam, tom),
        hasOtherDevices: Boolean = true,
        daemonReachable: Boolean = true,
        roundInProgress: Boolean = false,
        lastRoundAt: Long? = now - 20_000L,
        changesWaiting: Boolean = false,
        lastSyncedPeerName: String? = null,
        lastSyncedMsAgo: Long? = null,
    ) = SyncSnapshot(
        takenAt = now,
        peers = peers,
        hasOtherDevices = hasOtherDevices,
        daemonReachable = daemonReachable,
        roundInProgress = roundInProgress,
        lastRoundAt = lastRoundAt,
        changesWaiting = changesWaiting,
        lastSyncedPeerName = lastSyncedPeerName,
        lastSyncedMsAgo = lastSyncedMsAgo,
    )

    private fun event(id: Long, kind: SyncEventKind, module: SyncModule, peer: String, minutesAgo: Long = id, count: Int = 1, detail: String? = null) =
        SyncEventItem(id, now - minutesAgo * 60_000L, now - minutesAgo * 60_000L, kind, module, peer, detail, count)

    private class FakeSyncActivity(initial: SyncSnapshot, val log: List<SyncEventItem> = emptyList()) : SyncActivityRepository {
        override val available = true
        val snapshots = MutableStateFlow(initial)
        var syncNowCalls = 0

        override fun snapshot(homeId: String): Flow<SyncSnapshot> = snapshots

        override suspend fun events(homeId: String, filter: SyncEventFilter, peerAgent: String?, beforeLastAt: Long?, limit: Int): List<SyncEventItem> =
            log.filter {
                when (filter) {
                    SyncEventFilter.All -> true
                    SyncEventFilter.Received -> it.kind == SyncEventKind.Received
                    SyncEventFilter.Sent -> it.kind == SyncEventKind.Sent
                    SyncEventFilter.Problems -> it.kind == SyncEventKind.Failed
                }
            }.sortedByDescending { it.lastAt }.take(limit)

        override fun syncNow() {
            syncNowCalls++
        }
    }

    private fun ComposeUiTest.showScreen(repository: FakeSyncActivity, onInvite: () -> Unit = {}) {
        val viewModel = SyncActivityViewModel("home-1", repository, nowMillis = { now })
        setContent {
            VestaTheme {
                SyncActivityScreen(viewModel = viewModel, homeName = "The Johnsons", onBack = {}, onInvite = onInvite)
            }
        }
        waitForTag(SyncActivityTestTags.VerdictTitle)
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForNoTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun withTwoOtherDevicesOneSyncedAMinuteAgoTheScreenSaysUpToDateAndListsBoth() = runComposeUiTest {
        // Arrange — Given two other devices, Sam's online and synced a minute ago and Tom's offline
        val repository = FakeSyncActivity(snapshot(lastSyncedPeerName = "Sam's Pixel", lastSyncedMsAgo = 30_000L))

        // Act — When I open Sync activity
        showScreen(repository)

        // Assert — Then it says "Up to date" naming Sam's phone, and lists both devices with Tom's marked Offline
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Up to date", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("Sam's Pixel", substring = true)
        onNodeWithTag(SyncActivityTestTags.deviceRow("sam")).assertIsDisplayed().assertTextContains("Online", substring = true)
        onNodeWithTag(SyncActivityTestTags.deviceRow("tom")).assertIsDisplayed().assertTextContains("Offline", substring = true)
    }

    @Test
    fun withNobodyOnlineTheScreenSaysWaitingForOthersAndEarlierEventsRemain() = runComposeUiTest {
        // Arrange — Given nobody is online and a Tasks update arrived from Sam an hour ago
        val offlineSam = sam.copy(online = false, lastSyncedMsAgo = 3_600_000L)
        val repository = FakeSyncActivity(
            snapshot(peers = listOf(offlineSam, tom)),
            log = listOf(event(1, SyncEventKind.Received, SyncModule(VestaSyncModules.Tasks), "sam", minutesAgo = 60)),
        )

        // Act — When I open Sync activity
        showScreen(repository)

        // Assert — Then it says "Waiting for others" and the earlier event is still in the log
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Waiting for others", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("Nobody else is online", substring = true)
        waitForTag(SyncActivityTestTags.eventRow(1))
        onNodeWithText("Tasks updated by Sam's Pixel").assertIsDisplayed()
    }

    @Test
    fun theOnlyMemberOfAHomeSeesOnlyYouAndAnInviteRow() = runComposeUiTest {
        // Arrange — Given a Home that is only on this device
        var invites = 0
        val repository = FakeSyncActivity(snapshot(peers = emptyList(), hasOtherDevices = false))
        showScreen(repository, onInvite = { invites++ })

        // Act — When I tap the Invite row
        onNodeWithTag(SyncActivityTestTags.InviteRow).assertIsDisplayed().performClick()

        // Assert — Then the card says "Only you", there is no Devices list, and the invite flow was asked for
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Only you", substring = true)
        onAllNodesWithTag(SyncActivityTestTags.DevicesTitle).assertCountEquals(0)
        assertEquals(1, invites)
    }

    @Test
    fun aReceivedTasksUpdateReadsAsTasksUpdatedByTheDeviceWithItsTimeAndNoContent() = runComposeUiTest {
        // Arrange — Given Sam's phone sent a Tasks update three times inside five minutes, already folded into one row
        val repository = FakeSyncActivity(
            snapshot(),
            log = listOf(event(1, SyncEventKind.Received, SyncModule(VestaSyncModules.Tasks), "sam", minutesAgo = 2, count = 3)),
        )

        // Act — When I open Sync activity
        showScreen(repository)

        // Assert — Then one row says "Tasks updated by Sam's Pixel (3 times)" under a day header, and names no task
        waitForTag(SyncActivityTestTags.eventRow(1))
        onNodeWithText("Tasks updated by Sam's Pixel (3 times)").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.DayHeader).assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.Retention).assertTextContains("last 7 days", substring = true)
    }

    @Test
    fun aFailedSendAppearsUnderProblemsAndTheProblemsFilterShowsOnlyIt() = runComposeUiTest {
        // Arrange — Given a Pantry update that reached Sam and a send to Tom that failed
        val repository = FakeSyncActivity(
            snapshot(),
            log = listOf(
                event(1, SyncEventKind.Received, SyncModule(VestaSyncModules.Pantry), "sam", minutesAgo = 5),
                event(2, SyncEventKind.Failed, SyncModule(VestaSyncModules.Pantry), "tom", minutesAgo = 3, detail = "unreachable"),
            ),
        )
        showScreen(repository)
        waitForTag(SyncActivityTestTags.eventRow(2))

        // Act — When I choose the Problems filter
        onNodeWithTag(SyncActivityTestTags.FilterButton).performClick()
        onNodeWithTag(SyncActivityTestTags.filterOption(SyncEventFilter.Problems)).performClick()
        waitForNoTag(SyncActivityTestTags.eventRow(1))

        // Assert — Then only "Couldn't reach Tom's phone", with "Will keep trying", is listed
        onNodeWithText("Couldn't reach Tom's phone").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.eventRow(2)).assertTextContains("Will keep trying", substring = true)
        onAllNodesWithText("Pantry updated by Sam's Pixel").assertCountEquals(0)
    }

    @Test
    fun changesWaitingShowsUntilAPeerOrHubTakesThem() = runComposeUiTest {
        // Arrange — Given a local change nobody has taken and nobody online
        val offline = listOf(sam.copy(online = false, lastSyncedMsAgo = 3_600_000L), tom)
        val repository = FakeSyncActivity(snapshot(peers = offline, changesWaiting = true))
        showScreen(repository)
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Changes waiting", substring = true)

        // Act — When a hub takes the change, so nothing is left unsent
        repository.snapshots.value = snapshot(peers = offline, changesWaiting = false)
        waitForText("Waiting for others")

        // Assert — Then "Changes waiting" is gone
        onAllNodesWithText("Changes waiting", substring = true).assertCountEquals(0)
    }

    @Test
    fun syncNowRunsOneRoundAndShowsProgressUntilItEnds() = runComposeUiTest {
        // Arrange — Given an up to date Home
        val repository = FakeSyncActivity(snapshot(lastSyncedPeerName = "Sam's Pixel", lastSyncedMsAgo = 30_000L))
        showScreen(repository)

        // Act — When I tap Sync now, and the round starts and then ends
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsEnabled().performClick()
        waitForTag(SyncActivityTestTags.SyncNowProgress)
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsNotEnabled()
        repository.snapshots.value = snapshot(roundInProgress = true)
        repository.snapshots.value = snapshot(lastRoundAt = now, lastSyncedPeerName = "Sam's Pixel", lastSyncedMsAgo = 1_000L)
        waitForNoTag(SyncActivityTestTags.SyncNowProgress)

        // Assert — Then the sync service was asked for exactly one round, and the button is back
        assertEquals(1, repository.syncNowCalls)
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsEnabled()
    }

    @Test
    fun withTheDaemonDownTheCardSaysSyncIsNotRunningAndSyncNowIsDisabled() = runComposeUiTest {
        // Arrange — Given the x0x daemon cannot be reached
        val repository = FakeSyncActivity(snapshot(daemonReachable = false))

        // Act — When I open Sync activity
        showScreen(repository)

        // Assert — Then the card says sync isn't running and Sync now cannot be tapped
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("isn't running", substring = true)
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsNotEnabled()
        assertEquals(0, repository.syncNowCalls)
    }
}
