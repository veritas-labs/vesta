package uk.veritaslabs.vesta.ui.flows

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicepairing.DevicePairingViewModel
import uk.veritaslabs.vesta.ui.screens.DevicePairingGeneratedTestTags
import uk.veritaslabs.vesta.ui.screens.DevicePairingPasteTestTags
import uk.veritaslabs.vesta.ui.screens.DevicePairingRedeemedTestTags
import uk.veritaslabs.vesta.ui.screens.DevicePairingTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Drives the real [DevicePairingViewModel] (backed by an in-memory database)
 * through [DevicePairingScreen], [DevicePairingGeneratedScreen] and
 * [DevicePairingPasteScreen] together — the local pairing-link round trip
 * end to end, as far as ADR-003's current "Proposed" status allows (see
 * [DevicePairingViewModel]'s kdoc).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicePairingFlowTest {

    // The ViewModel's viewModelScope resolves to Dispatchers.Main, and the
    // underlying SQLite driver does its work on a real background thread —
    // a real dispatcher here lets that complete; a virtual-time test
    // dispatcher never would, since nothing here ever advances it.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun generatingAndThenPastingTheSameLinkRedeemsIt() = runComposeUiTest {
        // Arrange
        val database = openInMemoryDatabase()
        setContent {
            val viewModel = remember { DevicePairingViewModel(database) }
            VestaTheme { DevicePairingFlow(viewModel) }
        }

        // Act — generate a link on the picker screen, and read back exactly
        // what's shown on screen, the same way a user would copy it
        onNodeWithTag(DevicePairingTestTags.GenerateCard).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText("vesta://pair/", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val encodedLink = onNode(hasText("vesta://pair/", substring = true))
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }

        // Act — back out to the picker, then paste the same link back in
        onNodeWithTag(DevicePairingGeneratedTestTags.BackButton).performClick()
        onNodeWithTag(DevicePairingTestTags.PasteCard).performClick()
        onNodeWithTag(DevicePairingPasteTestTags.LinkField).performTextInput(encodedLink)
        onNodeWithTag(DevicePairingPasteTestTags.SubmitButton).performClick()

        // Assert — the round trip completes and confirms
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DevicePairingRedeemedTestTags.DoneButton).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevicePairingRedeemedTestTags.DoneButton).assertIsDisplayed()

        // Act — done returns to the picker
        onNodeWithTag(DevicePairingRedeemedTestTags.DoneButton).performClick()

        // Assert
        onNodeWithTag(DevicePairingTestTags.GenerateCard).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pastingGarbageShowsAnErrorInsteadOfRedeeming() = runComposeUiTest {
        // Arrange
        val database = openInMemoryDatabase()
        setContent {
            val viewModel = remember { DevicePairingViewModel(database) }
            VestaTheme { DevicePairingFlow(viewModel) }
        }

        // Act
        onNodeWithTag(DevicePairingTestTags.PasteCard).performClick()
        onNodeWithTag(DevicePairingPasteTestTags.LinkField).performTextInput("not a pairing link")
        onNodeWithTag(DevicePairingPasteTestTags.SubmitButton).performClick()

        // Assert
        onNodeWithTag(DevicePairingPasteTestTags.ErrorText).assertIsDisplayed()
    }
}
