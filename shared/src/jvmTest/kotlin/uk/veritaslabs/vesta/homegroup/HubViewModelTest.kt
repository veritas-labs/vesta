package uk.veritaslabs.vesta.homegroup

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @Test
    fun withNoHubTheScreenShowsTheEnrolmentCommands() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub()

        // Act
        val viewModel = HubViewModel("home-1", hubs)

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertFalse(state.unavailable)
        assertEquals(hubs.enrolment!!.commands, state.commands)
        assertEquals(emptyList(), state.hubs)
    }

    @Test
    fun theCommandsAreMadeOnceNotOnEveryPoll() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub()
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }

        // Act
        advanceTimeBy(10.seconds)

        // Assert
        assertEquals(1, hubs.enrolmentCalls)
        assertEquals(hubs.enrolment!!.commands, viewModel.uiState.value.commands)
    }

    @Test
    fun anAgentThatJoinsTheGroupAppearsAsACandidateOnTheNextPoll() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub()
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }
        assertEquals(emptyList(), viewModel.uiState.value.candidates)

        // Act
        hubs.candidates = listOf("agent-hub")
        advanceTimeBy(4.seconds)

        // Assert
        assertEquals(listOf("agent-hub"), viewModel.uiState.value.candidates)
    }

    @Test
    fun choosingACandidateRecordsItAndShowsTheHub() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { candidates = listOf("agent-hub") }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onUseAsHub("agent-hub")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(listOf("agent-hub"), hubs.used)
        assertEquals(listOf(HubItem("agent-hub", inGroup = true)), state.hubs)
        assertNull(state.commands)
        assertEquals(emptyList(), state.candidates)
        assertFalse(state.failed)
    }

    @Test
    fun aChoiceThatWasRefusedIsReportedAndTheCandidatesStay() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            candidates = listOf("agent-hub")
            useResult = HubResult.NotInGroup
        }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onUseAsHub("agent-hub")
        val failed = viewModel.uiState.value.failed
        viewModel.onDismissFailure()

        // Assert
        assertTrue(failed)
        assertFalse(viewModel.uiState.value.failed)
        assertEquals(emptyList(), viewModel.uiState.value.hubs)
        assertEquals(listOf("agent-hub"), viewModel.uiState.value.candidates)
    }

    @Test
    fun aRecordedHubIsShownWithWhetherItIsInTheGroupAndFollowsIt() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { hubIds = listOf("agent-hub") }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }
        val before = viewModel.uiState.value.hubs

        // Act
        hubs.inGroup = true
        advanceTimeBy(4.seconds)

        // Assert
        assertEquals(listOf(HubItem("agent-hub", inGroup = false)), before)
        assertEquals(listOf(HubItem("agent-hub", inGroup = true)), viewModel.uiState.value.hubs)
        assertEquals(0, hubs.enrolmentCalls)
    }

    @Test
    fun removingTheHubReturnsToTheEnrolmentCommands() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hubIds = listOf("agent-hub")
            inGroup = true
        }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onRemove("agent-hub")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(1, hubs.removeCalls)
        assertEquals(emptyList(), state.hubs)
        assertEquals(hubs.enrolment!!.commands, state.commands)
        assertFalse(state.failed)
    }

    @Test
    fun aRemovalThatFailedKeepsTheHubAndSaysSo() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hubIds = listOf("agent-hub")
            inGroup = true
            removeResult = HubResult.Failed
        }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onRemove("agent-hub")

        // Assert
        assertEquals(listOf(HubItem("agent-hub", inGroup = true)), viewModel.uiState.value.hubs)
        assertTrue(viewModel.uiState.value.failed)
    }

    @Test
    fun withoutEnrolmentCommandsTheHubIsUnavailableUntilTheyCanBeMade() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { enrolment = null }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }
        val first = viewModel.uiState.value

        // Act
        hubs.enrolment = HubEnrolment("group-1", "commands")
        advanceTimeBy(4.seconds)

        // Assert
        assertTrue(first.unavailable)
        assertFalse(viewModel.uiState.value.unavailable)
        assertEquals("commands", viewModel.uiState.value.commands)
    }

    @Test
    fun withoutAHubServiceItIsUnavailable() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)

        // Act
        val viewModel = HubViewModel("home-1", null)

        // Assert
        assertEquals(HubUiState(loading = false, unavailable = true), viewModel.uiState.value)
    }

    @Test
    fun aDaemonThatIsDownLeavesWhatWasShown() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { hubIds = listOf("agent-hub") }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }

        // Act
        hubs.failing = true
        advanceTimeBy(4.seconds)

        // Assert
        assertEquals(listOf(HubItem("agent-hub", inGroup = false)), viewModel.uiState.value.hubs)
    }

    @Test
    fun withAHubRecordedNoInviteIsMadeUntilTheAdminAsksToAddAnother() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { hubIds = listOf("agent-hub") }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }
        advanceTimeBy(7.seconds)
        val before = viewModel.uiState.value

        // Act
        viewModel.onAddAnother()
        val after = viewModel.uiState.value

        // Assert
        assertNull(before.commands)
        assertFalse(before.adding)
        assertTrue(after.adding)
        assertEquals(hubs.enrolment!!.commands, after.commands)
        assertEquals(1, hubs.enrolmentCalls)
    }

    @Test
    fun severalHubsAreShownAndEachIsRemovedOnItsOwn() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hubIds = listOf("agent-hub-1", "agent-hub-2")
            inGroup = true
            deviceHubs = setOf("agent-hub-2")
        }
        val viewModel = HubViewModel("home-1", hubs)
        val before = viewModel.uiState.value.hubs

        // Act
        viewModel.onRemove("agent-hub-1")

        // Assert
        assertEquals(listOf(HubItem("agent-hub-1", true), HubItem("agent-hub-2", true, isDevice = true)), before)
        assertEquals(listOf("agent-hub-1"), hubs.removed)
        assertEquals(listOf(HubItem("agent-hub-2", true, isDevice = true)), viewModel.uiState.value.hubs)
    }

    @Test
    fun choosingAnotherHubKeepsTheFirstAndLeavesTheAddingView() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hubIds = listOf("agent-hub-1")
            inGroup = true
            candidates = listOf("agent-hub-2")
        }
        val viewModel = HubViewModel("home-1", hubs)
        viewModel.onAddAnother()

        // Act
        viewModel.onUseAsHub("agent-hub-2")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(listOf("agent-hub-1", "agent-hub-2"), state.hubs.map { it.agentId })
        assertEquals(emptyList(), state.candidates)
    }

    @Test
    fun theUsersOwnDevicesAreOfferedOnlyWhereThePlatformAllowsIt() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { ownDevices = listOf("agent-desktop") }

        // Act
        val desktop = HubViewModel("home-1", hubs, offerOwnDevice = true).uiState.value
        val phone = HubViewModel("home-1", hubs).uiState.value

        // Assert
        assertEquals(listOf("agent-desktop"), desktop.ownDevices)
        assertEquals(emptyList(), phone.ownDevices)
    }

    @Test
    fun choosingTheUsersOwnDeviceMakesItAHub() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            ownDevices = listOf("agent-desktop")
            deviceHubs = setOf("agent-desktop")
        }
        val viewModel = HubViewModel("home-1", hubs, offerOwnDevice = true)

        // Act
        viewModel.onUseAsHub("agent-desktop")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(listOf(HubItem("agent-desktop", true, isDevice = true)), state.hubs)
        assertEquals(emptyList(), state.ownDevices)
    }

    @Test
    fun enrolmentCommandsOlderThanHalfAnHourAreMadeAgainBecauseTheirInviteWillLapse() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub()
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds, nowMillis = { testScheduler.currentTime })
        backgroundScope.launch { viewModel.poll() }

        // Act
        advanceTimeBy(29.minutes)
        val soon = hubs.enrolmentCalls
        advanceTimeBy(3.minutes)

        // Assert
        assertEquals(1, soon)
        assertEquals(2, hubs.enrolmentCalls)
    }
}
