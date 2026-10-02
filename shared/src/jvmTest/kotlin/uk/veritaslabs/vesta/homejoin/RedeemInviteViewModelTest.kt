package uk.veritaslabs.vesta.homejoin

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RedeemInviteViewModelTest {

    private val link = InviteLink("home-1", "s3cr3t", expiresAt = 1_000L, adminAgentIds = listOf("agent-a"), homeName = "Maple Street")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @Test
    fun aValidLinkAsksToJoin() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining()
        val viewModel = RedeemInviteViewModel(joining, nowMillis = { 0L })

        // Act
        viewModel.onRedeem(link.encode())

        // Assert
        assertEquals(RedeemInviteUiState.Started, viewModel.uiState.value)
        assertEquals(listOf(link), joining.redeemed)
    }

    @Test
    fun textThatIsNotALinkIsRejectedWithoutAsking() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining()
        val viewModel = RedeemInviteViewModel(joining, nowMillis = { 0L })

        // Act
        viewModel.onRedeem("hello")

        // Assert
        assertEquals(RedeemInviteUiState.Failed(RedeemInviteError.InvalidLink), viewModel.uiState.value)
        assertEquals(emptyList(), joining.redeemed)
    }

    @Test
    fun aLinkOfAnotherKindIsNotAnInviteLink() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = RedeemInviteViewModel(FakeHomeJoining(), nowMillis = { 0L })

        // Act
        viewModel.onRedeem("vesta://pair/YWJjOjEwMA")

        // Assert
        assertEquals(RedeemInviteUiState.Failed(RedeemInviteError.InvalidLink), viewModel.uiState.value)
    }

    @Test
    fun anExpiredLinkIsRejectedWithoutAsking() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining()
        val viewModel = RedeemInviteViewModel(joining, nowMillis = { 2_000L })

        // Act
        viewModel.onRedeem(link.encode())

        // Assert
        assertEquals(RedeemInviteUiState.Failed(RedeemInviteError.Expired), viewModel.uiState.value)
        assertEquals(emptyList(), joining.redeemed)
    }

    @Test
    fun aHomeAlreadyHeldIsReported() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val joining = FakeHomeJoining().apply { redeemResult = RedeemResult.AlreadyInHome }
        val viewModel = RedeemInviteViewModel(joining, nowMillis = { 0L })

        // Act
        viewModel.onRedeem(link.encode())

        // Assert
        assertEquals(RedeemInviteUiState.Failed(RedeemInviteError.AlreadyInHome), viewModel.uiState.value)
    }

    @Test
    fun withoutAJoiningServiceARequestFails() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = RedeemInviteViewModel(null, nowMillis = { 0L })

        // Act
        viewModel.onRedeem(link.encode())

        // Assert
        assertEquals(RedeemInviteUiState.Failed(RedeemInviteError.Failed), viewModel.uiState.value)
    }

    @Test
    fun resettingReturnsToTheEmptyForm() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val viewModel = RedeemInviteViewModel(FakeHomeJoining(), nowMillis = { 0L })
        viewModel.onRedeem("hello")

        // Act
        viewModel.onReset()

        // Assert
        assertEquals(RedeemInviteUiState.Idle, viewModel.uiState.value)
    }
}
