package uk.veritaslabs.vesta.homejoin

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeInviteViewModelTest {

    private class FakeJoining(private val result: Result<Unit>) : HomeJoining {
        val invited = mutableListOf<Pair<String, JoinLink>>()
        override val joined: SharedFlow<JoinedHome> = MutableSharedFlow()
        override suspend fun openLink() = error("not used")
        override fun cancelLink() = Unit
        override suspend fun invite(homeId: String, link: JoinLink): Result<Unit> {
            invited += homeId to link
            return result
        }
    }

    private val link = JoinLink("agent-y", "member-y", expiresAt = 1_000L, name = "Sam")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) =
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @Test
    fun aValidLinkInvitesThemToTheHome() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeJoining(Result.success(Unit))
        val viewModel = HomeInviteViewModel("home-1", joining, nowMillis = { 0L })

        // Act
        viewModel.onInvite(link.encode())
        val state = viewModel.uiState.first { it is HomeInviteUiState.Invited || it is HomeInviteUiState.Failed }

        // Assert
        assertEquals(HomeInviteUiState.Invited, state)
        assertEquals(listOf("home-1" to link), joining.invited)
    }

    @Test
    fun textThatIsNotALinkIsRejectedWithoutInviting() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeJoining(Result.success(Unit))
        val viewModel = HomeInviteViewModel("home-1", joining, nowMillis = { 0L })

        // Act
        viewModel.onInvite("hello")

        // Assert
        assertEquals(HomeInviteUiState.Failed(HomeInviteError.InvalidLink), viewModel.uiState.value)
        assertEquals(emptyList(), joining.invited)
    }

    @Test
    fun anExpiredLinkIsRejectedWithoutInviting() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeJoining(Result.success(Unit))
        val viewModel = HomeInviteViewModel("home-1", joining, nowMillis = { 2_000L })

        // Act
        viewModel.onInvite(link.encode())

        // Assert
        assertEquals(HomeInviteUiState.Failed(HomeInviteError.Expired), viewModel.uiState.value)
        assertEquals(emptyList(), joining.invited)
    }

    @Test
    fun aFailedInvitationShowsItsReason() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeJoining(Result.failure(InviteException(InviteFailure.AlreadyAMember)))
        val viewModel = HomeInviteViewModel("home-1", joining, nowMillis = { 0L })

        // Act
        viewModel.onInvite(link.encode())

        // Assert
        assertEquals(HomeInviteUiState.Failed(HomeInviteError.AlreadyAMember), viewModel.uiState.value)
    }

    @Test
    fun resettingReturnsToTheEmptyForm() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = HomeInviteViewModel("home-1", FakeJoining(Result.success(Unit)), nowMillis = { 0L })
        viewModel.onInvite("hello")

        // Act
        viewModel.onReset()

        // Assert
        assertEquals(HomeInviteUiState.Idle, viewModel.uiState.value)
    }
}
