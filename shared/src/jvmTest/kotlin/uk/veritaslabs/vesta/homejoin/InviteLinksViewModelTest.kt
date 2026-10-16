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

import uk.veritaslabs.kits.spaces.ActiveInvite
import uk.veritaslabs.kits.spaces.InviteLink
@OptIn(ExperimentalCoroutinesApi::class)
class InviteLinksViewModelTest {

    private val day = 24 * 60 * 60 * 1000L
    private val link = InviteLink("home-1", "s3cr3t", expiresAt = 7 * day, adminAgentIds = listOf("agent-a"), spaceName = "Maple Street")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @Test
    fun openingTheScreenMakesALinkToShow() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { created = link }

        // Act
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })

        // Assert
        assertEquals(InviteLinksUiState(created = link), viewModel.uiState.value)
        assertEquals(1, joining.createCalls)
    }

    @Test
    fun aLinkThatCouldNotBeMadeIsReported() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)

        // Act
        val viewModel = InviteLinksViewModel("home-1", FakeHomeJoining(), nowMillis = { 0L })

        // Assert
        assertNull(viewModel.uiState.value.created)
        assertEquals(true, viewModel.uiState.value.failed)
    }

    @Test
    fun withoutAJoiningServiceNoLinkCanBeMade() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)

        // Act
        val viewModel = InviteLinksViewModel("home-1", null, nowMillis = { 0L })

        // Assert
        assertEquals(InviteLinksUiState(failed = true), viewModel.uiState.value)
    }

    @Test
    fun managingListsTheActiveLinksWithTheDaysTheyHaveLeft() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply {
            created = link
            links += ActiveInvite("a", expiresAt = 3 * day + 5, uses = 1, maxUses = 2)
        }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })

        // Act
        viewModel.onManage()

        // Assert
        assertEquals(true, viewModel.uiState.value.managing)
        assertEquals(listOf(InviteLinkItem("a", daysLeft = 3, uses = 1, maxUses = 2)), viewModel.uiState.value.links)
    }

    @Test
    fun revokingALinkRemovesItFromTheList() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply {
            created = link
            links += ActiveInvite("a", 7 * day, 0, 1)
        }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })
        viewModel.onManage()

        // Act
        viewModel.onRevoke("a")

        // Assert
        assertEquals(listOf("home-1" to "a"), joining.revoked)
        assertEquals(emptyList(), viewModel.uiState.value.links)
    }

    @Test
    fun goingBackFromManagingKeepsTheLinkThatWasShown() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { created = link }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })
        viewModel.onManage()

        // Act
        viewModel.onStopManaging()

        // Assert
        assertEquals(InviteLinksUiState(created = link), viewModel.uiState.value)
        assertEquals(1, joining.createCalls)
    }

    @Test
    fun goingBackAfterRevokingMakesANewLinkInCaseTheShownOneWasRevoked() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply {
            created = link
            links += ActiveInvite("a", 7 * day, 0, 1)
        }
        val viewModel = InviteLinksViewModel("home-1", joining, nowMillis = { 0L })
        viewModel.onManage()
        viewModel.onRevoke("a")

        // Act
        viewModel.onStopManaging()

        // Assert
        assertEquals(2, joining.createCalls)
        assertEquals(link, viewModel.uiState.value.created)
        assertEquals(false, viewModel.uiState.value.managing)
    }
}
