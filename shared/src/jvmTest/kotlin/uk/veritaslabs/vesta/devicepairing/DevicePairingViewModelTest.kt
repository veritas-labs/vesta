package uk.veritaslabs.vesta.devicepairing

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.db.openInMemoryDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class DevicePairingViewModelTest {

    // The underlying SQLite driver does its work on a real background thread, so the
    // ViewModel's viewModelScope (Dispatchers.Main.immediate) needs a real dispatcher
    // here too — a virtual-time test dispatcher would never see that work complete.
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun generatingAPairingLinkPersistsItAndShowsIt() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        val viewModel = DevicePairingViewModel(database, nowMillis = { 1_000L }, agentId = { "aa11bb" })

        // Act
        viewModel.onGeneratePairingLink()
        viewModel.awaitUiState { it is DevicePairingUiState.Generated }

        // Assert
        val state = assertIs<DevicePairingUiState.Generated>(viewModel.uiState.value)
        assertEquals(PairingLink(agentId = "aa11bb", expiresAt = 1_000L + 5 * 60 * 1000L), state.link)
        val persisted = database.devicePairingLinkQueries.selectActive(1_000L).awaitAsOneOrNull()
        assertEquals("aa11bb", persisted?.publicKeyHex)
    }

    @Test
    fun generatingAPairingLinkAgainReplacesTheEarlierOne() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        var now = 1_000L
        val viewModel = DevicePairingViewModel(database, nowMillis = { now }, agentId = { "aa11bb" })
        viewModel.onGeneratePairingLink()
        viewModel.awaitUiState { it is DevicePairingUiState.Generated }
        viewModel.onBackToPicker() // leaves the first link stored, as backing out does

        // Act
        now = 2_000L
        viewModel.onGeneratePairingLink()
        viewModel.awaitUiState { (it as? DevicePairingUiState.Generated)?.link?.expiresAt == 2_000L + 5 * 60 * 1000L }

        // Assert
        val persisted = database.devicePairingLinkQueries.selectActive(2_000L).awaitAsOneOrNull()
        assertEquals(2_000L + 5 * 60 * 1000L, persisted?.expiresAt)
    }

    @Test
    fun submittingTheGeneratedLinkRedeemsItAndConsumesTheLocalRecord() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        val viewModel = DevicePairingViewModel(database, nowMillis = { 1_000L }, agentId = { "cc22dd" })
        viewModel.onGeneratePairingLink()
        viewModel.awaitUiState { it is DevicePairingUiState.Generated }
        val generatedLink = (viewModel.uiState.value as DevicePairingUiState.Generated).link
        viewModel.onPastePairingLink()

        // Act
        viewModel.onSubmitPairingLink(generatedLink.encode())
        viewModel.awaitUiState { it is DevicePairingUiState.Paste && it.redeemedLink != null }

        // Assert
        val state = assertIs<DevicePairingUiState.Paste>(viewModel.uiState.value)
        assertEquals(generatedLink, state.redeemedLink)
        assertNull(state.error)
        assertNull(database.devicePairingLinkQueries.selectActive(1_000L).awaitAsOneOrNull())
    }

    @Test
    fun submittingUndecodableTextShowsInvalidFormatError() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        val viewModel = DevicePairingViewModel(database)
        viewModel.onPastePairingLink()

        // Act
        viewModel.onSubmitPairingLink("not a pairing link")

        // Assert
        val state = assertIs<DevicePairingUiState.Paste>(viewModel.uiState.value)
        assertEquals(DevicePairingPasteError.InvalidFormat, state.error)
        assertNull(state.redeemedLink)
    }

    @Test
    fun submittingAnExpiredLinkShowsExpiredError() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        val viewModel = DevicePairingViewModel(database, nowMillis = { 5_000L })
        viewModel.onPastePairingLink()
        val expiredLink = PairingLink(agentId = "ee33ff", expiresAt = 1_000L)

        // Act
        viewModel.onSubmitPairingLink(expiredLink.encode())

        // Assert
        val state = assertIs<DevicePairingUiState.Paste>(viewModel.uiState.value)
        assertEquals(DevicePairingPasteError.Expired, state.error)
        assertNull(state.redeemedLink)
    }

    @Test
    fun cancellingAGeneratedLinkDeletesItAndReturnsToPicker() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val database = openInMemoryDatabase()
        val viewModel = DevicePairingViewModel(database, nowMillis = { 1_000L }, agentId = { "0011ff" })
        viewModel.onGeneratePairingLink()
        viewModel.awaitUiState { it is DevicePairingUiState.Generated }
        val generatedLink = (viewModel.uiState.value as DevicePairingUiState.Generated).link

        // Act
        viewModel.onCancelGenerated(generatedLink)
        viewModel.awaitUiState { it is DevicePairingUiState.Picker }

        // Assert
        assertEquals(DevicePairingUiState.Picker, viewModel.uiState.value)
        assertNull(database.devicePairingLinkQueries.selectActive(1_000L).awaitAsOneOrNull())
    }
}

private suspend fun DevicePairingViewModel.awaitUiState(
    timeoutMillis: Long = 5_000,
    predicate: (DevicePairingUiState) -> Boolean,
) {
    withTimeout(timeoutMillis) {
        while (!predicate(uiState.value)) delay(5)
    }
}
