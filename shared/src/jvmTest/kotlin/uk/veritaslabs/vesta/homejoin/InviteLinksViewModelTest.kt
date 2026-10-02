package uk.veritaslabs.vesta.homejoin

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class InviteLinksViewModelTest {

    private val day = 24 * 60 * 60 * 1000L
    private val link = InviteLink("home-1", "s3cr3t", expiresAt = 7 * day, adminAgentIds = listOf("agent-a"), homeName = "Maple Street")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @Test
    fun theActiveLinksAreListedWithTheDaysTheyHaveLeft() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { links += ActiveInvite("a", expiresAt = 3 * day + 5, uses = 1, maxUses = 2) }

        // Act
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })

        // Assert
        assertEquals(listOf(InviteLinkItem("a", daysLeft = 3, uses = 1, maxUses = 2)), viewModel.uiState.value.links)
    }

    @Test
    fun creatingALinkShowsItAndListsTheNewOne() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { created = link }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })
        joining.links += ActiveInvite("new", 7 * day, 0, 1)

        // Act
        viewModel.onCreate()

        // Assert
        assertEquals(link, viewModel.uiState.value.created)
        assertEquals(false, viewModel.uiState.value.failed)
        assertEquals(listOf("new"), viewModel.uiState.value.links.map { it.id })
    }

    @Test
    fun aLinkThatCouldNotBeMadeIsReported() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = InviteLinksViewModel("home-1", FakeHomeJoining(), nowMillis = { 0L })

        // Act
        viewModel.onCreate()

        // Assert
        assertNull(viewModel.uiState.value.created)
        assertEquals(true, viewModel.uiState.value.failed)
    }

    @Test
    fun dismissingTheNewLinkReturnsToTheList() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = InviteLinksViewModel("home-1", FakeHomeJoining().apply { created = link }, nowMillis = { 0L })
        viewModel.onCreate()

        // Act
        viewModel.onDismissCreated()

        // Assert
        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun revokingALinkRemovesItFromTheList() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { links += ActiveInvite("a", 7 * day, 0, 1) }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })

        // Act
        viewModel.onRevoke("a")

        // Assert
        assertEquals(listOf("home-1" to "a"), joining.revoked)
        assertEquals(emptyList(), viewModel.uiState.value.links)
    }

    @Test
    fun withoutAJoiningServiceThereAreNoLinksAndNoneCanBeMade() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = InviteLinksViewModel("home-1", null, nowMillis = { 0L })

        // Act
        viewModel.onCreate()

        // Assert
        assertEquals(InviteLinksUiState(failed = true), viewModel.uiState.value)
    }
}
