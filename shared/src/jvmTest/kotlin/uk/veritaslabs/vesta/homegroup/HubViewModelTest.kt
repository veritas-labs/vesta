package uk.veritaslabs.vesta.homegroup

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        assertNull(state.hub)
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
        assertEquals(HubItem("agent-hub", inGroup = true), state.hub)
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
        assertNull(viewModel.uiState.value.hub)
        assertEquals(listOf("agent-hub"), viewModel.uiState.value.candidates)
    }

    @Test
    fun aRecordedHubIsShownWithWhetherItIsInTheGroupAndFollowsIt() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply { hub = "agent-hub" }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }
        val before = viewModel.uiState.value.hub

        // Act
        hubs.inGroup = true
        advanceTimeBy(4.seconds)

        // Assert
        assertEquals(HubItem("agent-hub", inGroup = false), before)
        assertEquals(HubItem("agent-hub", inGroup = true), viewModel.uiState.value.hub)
        assertEquals(0, hubs.enrolmentCalls)
    }

    @Test
    fun removingTheHubReturnsToTheEnrolmentCommands() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hub = "agent-hub"
            inGroup = true
        }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onRemove()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(1, hubs.removeCalls)
        assertNull(state.hub)
        assertEquals(hubs.enrolment!!.commands, state.commands)
        assertFalse(state.failed)
    }

    @Test
    fun aRemovalThatFailedKeepsTheHubAndSaysSo() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val hubs = FakeHomeHub().apply {
            hub = "agent-hub"
            inGroup = true
            removeResult = HubResult.Failed
        }
        val viewModel = HubViewModel("home-1", hubs)

        // Act
        viewModel.onRemove()

        // Assert
        assertEquals(HubItem("agent-hub", inGroup = true), viewModel.uiState.value.hub)
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
        val hubs = FakeHomeHub().apply { hub = "agent-hub" }
        val viewModel = HubViewModel("home-1", hubs, pollInterval = 3.seconds)
        backgroundScope.launch { viewModel.poll() }

        // Act
        hubs.failing = true
        advanceTimeBy(4.seconds)

        // Assert
        assertEquals(HubItem("agent-hub", inGroup = false), viewModel.uiState.value.hub)
    }
}
