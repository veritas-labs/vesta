package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.syncactivity.PeerActivity
import uk.veritaslabs.vesta.syncactivity.ProblemReason
import uk.veritaslabs.vesta.syncactivity.SyncActivityUiState
import uk.veritaslabs.vesta.syncactivity.SyncEventFilter
import uk.veritaslabs.vesta.syncactivity.SyncEventItem
import uk.veritaslabs.vesta.syncactivity.SyncEventKind
import uk.veritaslabs.vesta.syncactivity.SyncModuleName
import uk.veritaslabs.vesta.syncactivity.SyncVerdict
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class SyncActivityScreenTest {

    private val now = 1_792_000_000_000L
    private val sam = PeerActivity("sam", "Sam's Pixel", ownerName = "Sam", online = true, lastSyncedMsAgo = 120_000L)
    private val tom = PeerActivity("tom", "Tom's phone", ownerName = "Tom", online = false, lastSyncedMsAgo = 3 * 86_400_000L)
    private val hub = PeerActivity("hub", "Kitchen hub", isHub = true, online = true, lastSyncedMsAgo = 60_000L)

    private fun state(
        verdict: SyncVerdict? = SyncVerdict.UpToDate("Sam's Pixel", 120_000L),
        peers: List<PeerActivity> = listOf(sam, tom, hub),
        events: List<SyncEventItem> = emptyList(),
        syncing: Boolean = false,
        syncAvailable: Boolean = true,
        canLoadOlder: Boolean = false,
        isLoading: Boolean = false,
        filter: SyncEventFilter = SyncEventFilter.All,
    ) = SyncActivityUiState(
        isLoading = isLoading,
        verdict = verdict,
        peers = peers,
        events = events,
        filter = filter,
        canLoadOlder = canLoadOlder,
        syncing = syncing,
        syncAvailable = syncAvailable,
    )

    private fun event(id: Long, kind: SyncEventKind, module: SyncModuleName, peer: String?, detail: String? = null, count: Int = 1) =
        SyncEventItem(id, now - id * 60_000L, now - id * 60_000L, kind, module, peer, detail, count)

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.show(
        state: SyncActivityUiState,
        onSyncNow: () -> Unit = {},
        onFilter: (SyncEventFilter) -> Unit = {},
        onSelectDevice: (String) -> Unit = {},
        onLoadOlder: () -> Unit = {},
        onInvite: () -> Unit = {},
        compact: Boolean = false,
    ) = setContent {
        VestaTheme {
            Box(if (compact) Modifier.size(400.dp, 800.dp) else Modifier.size(1100.dp, 800.dp)) {
                SyncActivityScreen(
                    state = state,
                    homeName = "The Johnsons",
                    nowMillis = now,
                    onSyncNow = onSyncNow,
                    onFilter = onFilter,
                    onSelectDevice = onSelectDevice,
                    onLoadOlder = onLoadOlder,
                    onInvite = onInvite,
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun upToDateSaysSoAndNamesTheDevice() = runComposeUiTest {
        // Arrange
        show(state())

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Up to date", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("Sam's Pixel", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun waitingForOthersSaysNobodyIsOnline() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.WaitingForOthers))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Waiting for others", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("Nobody else is online", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun changesWaitingSaysChangesAreWaiting() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.ChangesWaiting))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Changes waiting", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aProblemSaysSyncIsNotRunningAndGivesTheReason() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.Problem(ProblemReason.DaemonUnreachable)))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("isn't running", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("can't reach its sync service", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncingShowsProgressInsteadOfTheVerdictIcon() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.Syncing, syncing = true))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Syncing", substring = true)
        onNodeWithTag(SyncActivityTestTags.SyncNowProgress).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncNowIsDisabledWhileSyncing() = runComposeUiTest {
        // Arrange
        show(state(syncing = true))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsNotEnabled()
        onNodeWithTag(SyncActivityTestTags.SyncNowProgress).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncNowIsDisabledWhenSyncIsNotAvailable() = runComposeUiTest {
        // Arrange
        show(state(syncAvailable = false))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncNowIsDisabledWhenTheDaemonIsDown() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.Problem(ProblemReason.DaemonUnreachable)))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncNowReportsTheClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        show(state(), onSyncNow = { clicks++ })

        // Act
        onNodeWithTag(SyncActivityTestTags.SyncNow).assertIsEnabled().performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anOfflineDeviceIsListedAndSaysOffline() = runComposeUiTest {
        // Arrange
        show(state())

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.deviceRow("tom")).assertIsDisplayed().assertTextContains("Offline", substring = true)
        onNodeWithTag(SyncActivityTestTags.deviceRow("sam")).assertTextContains("Online", substring = true)
        onNodeWithTag(SyncActivityTestTags.deviceRow("tom")).assertTextContains("Tom", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onlyYouShowsTheInviteRowInsteadOfDevices() = runComposeUiTest {
        // Arrange
        var invites = 0
        show(state(verdict = SyncVerdict.OnlyYou, peers = emptyList()), onInvite = { invites++ })

        // Act
        onNodeWithTag(SyncActivityTestTags.InviteRow).performClick()

        // Assert
        assertEquals(1, invites)
        onAllNodesWithTag(SyncActivityTestTags.DevicesTitle).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theFilterMenuReportsTheChosenFilter() = runComposeUiTest {
        // Arrange
        val chosen = mutableListOf<SyncEventFilter>()
        show(state(), onFilter = { chosen += it })

        // Act
        onNodeWithTag(SyncActivityTestTags.FilterButton).performClick()
        onNodeWithTag(SyncActivityTestTags.filterOption(SyncEventFilter.Problems)).performClick()

        // Assert
        assertEquals(listOf(SyncEventFilter.Problems), chosen)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theLogReadsEachEntryAsOneSentence() = runComposeUiTest {
        // Arrange
        val events = listOf(
            event(1, SyncEventKind.Received, SyncModuleName.Tasks, "sam"),
            event(2, SyncEventKind.Sent, SyncModuleName.Shopping, "tom", count = 3),
        )
        show(state(events = events))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Tasks updated by Sam's Pixel").assertIsDisplayed()
        onNodeWithText("Shopping list sent to Tom's phone (3 times)").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun changesFromANewerVersionSaySoOnTheCard() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.NeedsNewerVersion))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.VerdictTitle).assertTextContains("Some changes need a newer Vesta", substring = true)
        onNodeWithTag(SyncActivityTestTags.VerdictBody).assertTextContains("Update Vesta", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anUnsupportedVersionEntryNamesTheDeviceAndWhatItSent() = runComposeUiTest {
        // Arrange
        val events = listOf(
            event(1, SyncEventKind.UnsupportedVersion, SyncModuleName.Mailbox, "tom", detail = "v2", count = 2),
            event(2, SyncEventKind.UnknownMessage, SyncModuleName.Members, "sam", detail = "devicesync.teleport"),
        )
        show(state(events = events, filter = SyncEventFilter.Problems))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Tom's phone sent changes this version can't read (2 times)").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.eventRow(1)).assertTextContains("v2", substring = true)
        onNodeWithText("Sam's Pixel sent a message this version doesn't know").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.eventRow(2)).assertTextContains("devicesync.teleport", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aProblemEntryNamesTheDeviceAndSaysItWillKeepTrying() = runComposeUiTest {
        // Arrange
        val events = listOf(event(1, SyncEventKind.Failed, SyncModuleName.Pantry, "tom", detail = "unreachable"))
        show(state(events = events, filter = SyncEventFilter.Problems))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Couldn't reach Tom's phone").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.eventRow(1)).assertTextContains("Will keep trying", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anEntryFromAnUnknownDeviceUsesAGenericName() = runComposeUiTest {
        // Arrange
        val events = listOf(event(1, SyncEventKind.Received, SyncModuleName.Pantry, "stranger"))
        show(state(events = events))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Pantry updated by Another device").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun entriesSitUnderADayHeader() = runComposeUiTest {
        // Arrange
        show(state(events = listOf(event(1, SyncEventKind.Received, SyncModuleName.Tasks, "sam"))))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.DayHeader).assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.Retention).assertTextContains("last 7 days", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showOlderReportsTheClickAndOnlyShowsWhenThereIsMore() = runComposeUiTest {
        // Arrange
        var clicks = 0
        val events = listOf(event(1, SyncEventKind.Received, SyncModuleName.Tasks, "sam"))
        show(state(events = events, canLoadOlder = true), onLoadOlder = { clicks++ })

        // Act
        onNodeWithTag(SyncActivityTestTags.ShowOlder).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showOlderIsAbsentWhenThereIsNoMore() = runComposeUiTest {
        // Arrange
        show(state(events = listOf(event(1, SyncEventKind.Received, SyncModuleName.Tasks, "sam")), canLoadOlder = false))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onAllNodesWithTag(SyncActivityTestTags.ShowOlder).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anEmptyLogSaysNothingHasSyncedAndGivesTheReason() = runComposeUiTest {
        // Arrange
        show(state(verdict = SyncVerdict.WaitingForOthers, events = emptyList()))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Nothing has synced yet.").assertIsDisplayed()
        onNodeWithTag(SyncActivityTestTags.EmptyReason).assertTextContains("Waiting for another device", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun whileLoadingThePageIsBlank() = runComposeUiTest {
        // Arrange
        show(state(isLoading = true, verdict = null, peers = emptyList()))

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncActivityTestTags.Loading).assertIsDisplayed()
        onAllNodesWithTag(SyncActivityTestTags.StatusCard).assertCountEquals(0)
        onAllNodesWithTag(SyncActivityTestTags.EmptyLog).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onATabletTappingADeviceSelectsItAndShowsItsDetailsInline() = runComposeUiTest {
        // Arrange
        val selected = mutableListOf<String>()
        show(state().copy(selectedDevice = "tom"), onSelectDevice = { selected += it })

        // Act
        onNodeWithTag(SyncActivityTestTags.deviceRow("sam")).performClick()

        // Assert
        assertEquals(listOf("sam"), selected)
        onNodeWithTag(SyncActivityTestTags.DeviceDetail).assertIsDisplayed()
        onAllNodesWithText("Tom's phone").assertCountEquals(2)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onAPhoneTappingADeviceOpensItsDetailsInASheet() = runComposeUiTest {
        // Arrange
        show(state(), compact = true)

        // Act
        onNodeWithTag(SyncActivityTestTags.deviceRow("tom")).performClick()

        // Assert
        onNodeWithTag(SyncActivityTestTags.DeviceDetail).assertIsDisplayed()
        onAllNodesWithText("Tom's phone").assertCountEquals(2)
        onNodeWithTag(SyncActivityTestTags.ShowDeviceActivity).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theDeviceSheetSaysWhenAMessageIsBeingRetried() = runComposeUiTest {
        // Arrange
        val retrying = tom.copy(retryingSinceMsAgo = 5 * 60_000L)
        show(state(peers = listOf(sam, retrying)), compact = true)

        // Act
        onNodeWithTag(SyncActivityTestTags.deviceRow("tom")).performClick()

        // Assert
        onNodeWithTag(SyncActivityTestTags.DeviceRetrying).assertTextContains("Retrying", substring = true)
    }
}
