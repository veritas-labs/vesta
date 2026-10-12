package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.MerchantsDocumentSource
import uk.veritaslabs.vesta.ui.flows.MerchantsFlow
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.screens.MarketplaceScreen
import uk.veritaslabs.vesta.ui.screens.MarketplaceTestTags
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantDetailTestTags
import uk.veritaslabs.vesta.ui.screens.MerchantEditTestTags
import uk.veritaslabs.vesta.ui.screens.MerchantService
import uk.veritaslabs.vesta.ui.screens.MerchantType
import uk.veritaslabs.vesta.ui.screens.MerchantsTestTags
import uk.veritaslabs.vesta.ui.screens.MerchantsViewModel
import uk.veritaslabs.vesta.ui.screens.OpenRange
import uk.veritaslabs.vesta.ui.screens.OpeningHours
import uk.veritaslabs.vesta.ui.screens.Weekday
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for Merchants (docs/adr/ADR-026-merchants.md; see docs/testing/acceptance-tests.md). Each test is one
 * scenario, driven only through what a user can see and tap, over the real Automerge-backed merchants document, the view
 * model and the list, detail and form screens. The criteria:
 *
 *  1. The Home's dashboard offers the Marketplace, whose one option, Local Merchants, opens the merchants.
 *  2. I can add a merchant with a name, type, services, opening hours, location, GERS ID and x0x agent ID, and see them all
 *     on its page.
 *  3. A location needs both coordinates, within range, and a time must be a time; otherwise I can't save.
 *  4. I can edit a merchant, including clearing its location.
 *  5. I can delete a merchant after confirming.
 *  6. I can filter by type and search by name, address or service.
 *  7. Merchants survive an app restart and reach a paired device.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MerchantsAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val butcher = Merchant(
        name = "Hargreaves & Son",
        type = MerchantType.IndependentTrader,
        services = listOf(MerchantService.Butcher, MerchantService.Deli),
        hours = OpeningHours(mapOf(Weekday.Monday to listOf(OpenRange(9 * 60, 17 * 60)))),
        latitude = 53.4808,
        longitude = -2.2426,
        address = "12 Market Street",
    )
    private val tesco = Merchant(name = "Tesco Extra", type = MerchantType.NationalChain, services = listOf(MerchantService.Groceries, MerchantService.Pharmacy))
    private val veg = Merchant(name = "Dave's Veg Stall", type = MerchantType.MarketTrader, customServices = listOf("Eggs"))

    private fun newSource(bytes: ByteArray? = null, persist: suspend (ByteArray) -> Unit = {}) =
        MerchantsDocumentSource(bytes?.let { Document.load(it) } ?: Document(), persist)

    /** The merchants as the app shows them: the list, a merchant's page and the form. */
    private fun ComposeUiTest.showMerchants(source: MerchantsDocumentSource) {
        setContent {
            val viewModel = androidx.compose.runtime.remember { MerchantsViewModel(source) }
            VestaTheme { MerchantsFlow(viewModel, onBack = {}) }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openMerchant(name: String) {
        waitForText(name)
        onNodeWithText(name).performClick()
    }

    @Test
    fun theDashboardLeadsThroughTheMarketplaceToLocalMerchants() = runComposeUiTest {
        // Arrange — Given a Home's dashboard whose Marketplace tile opens the Marketplace, and Local Merchants opens the merchants
        var screen by androidx.compose.runtime.mutableStateOf("dashboard")
        setContent {
            VestaTheme {
                when (screen) {
                    "dashboard" -> HomeDashboardScreen(homeName = "Maple Street", onMarketplaceClick = { screen = "marketplace" })
                    "marketplace" -> MarketplaceScreen(onBack = { screen = "dashboard" }, onLocalMerchantsClick = { screen = "merchants" })
                    else -> MerchantsFlow(androidx.compose.runtime.remember { MerchantsViewModel(newSource()) }, onBack = { screen = "marketplace" })
                }
            }
        }

        // Act — When I tap the Marketplace tile, then Local Merchants
        onNodeWithTag(HomeDashboardTestTags.MarketplaceTile).performClick()
        onNodeWithText("Local Merchants").assertIsDisplayed()
        onNodeWithTag(MarketplaceTestTags.LocalMerchants).performClick()

        // Assert — Then the merchants open, and back returns to the Marketplace
        waitForText("No merchants yet")
        onNodeWithTag(MerchantsTestTags.BackButton).performClick()
        onNodeWithText("Local Merchants").assertIsDisplayed()
    }

    @Test
    fun addingAMerchantAndOpeningItShowsEverythingIEntered() = runComposeUiTest {
        // Arrange — Given no merchants yet
        showMerchants(newSource())
        waitForText("No merchants yet")

        // Act — When I add a market trader with services, Saturday hours, a location, a GERS ID and an agent
        onNodeWithTag(MerchantsTestTags.AddMerchantButton).performClick()
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Dave's Veg Stall")
        onNodeWithTag(MerchantEditTestTags.typeChip(MerchantType.MarketTrader)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.serviceChip(MerchantService.Greengrocer)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.CustomServicesField).performScrollTo().performTextInput("Eggs")
        onNodeWithTag(MerchantEditTestTags.addRange(Weekday.Saturday)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.openField(Weekday.Saturday, 0)).performScrollTo().performTextInput("08:00")
        onNodeWithTag(MerchantEditTestTags.closeField(Weekday.Saturday, 0)).performScrollTo().performTextInput("16:00")
        onNodeWithTag(MerchantEditTestTags.HoursNoteField).performScrollTo().performTextInput("Market days only")
        onNodeWithTag(MerchantEditTestTags.AddressField).performScrollTo().performTextInput("Stall 14, north aisle")
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextInput("53.4808")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextInput("-2.2426")
        onNodeWithTag(MerchantEditTestTags.GersIdField).performScrollTo().performTextInput("08f194ad3a5a5c1b")
        onNodeWithTag(MerchantEditTestTags.AgentIdField).performScrollTo().performTextInput("abcd1234")
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()
        waitForText("Dave's Veg Stall")

        // Assert — Then the list shows it, and its page shows every detail
        // The type shows on its row and on the filter chip for it.
        onAllNodesWithText("Market trader").assertCountEquals(2)
        onNodeWithText("Greengrocer, Eggs").assertIsDisplayed()
        openMerchant("Dave's Veg Stall")
        onNodeWithTag(MerchantDetailTestTags.service("Greengrocer")).assertIsDisplayed()
        onNodeWithTag(MerchantDetailTestTags.service("Eggs")).assertIsDisplayed()
        onNodeWithText("08:00–16:00").assertIsDisplayed()
        onNodeWithTag(MerchantDetailTestTags.HoursNote).assertTextEquals("Market days only")
        onNodeWithTag(MerchantDetailTestTags.Address).assertTextEquals("Stall 14, north aisle")
        onNodeWithTag(MerchantDetailTestTags.Coordinates).assertTextEquals("53.4808, -2.2426")
        onNodeWithTag(MerchantDetailTestTags.GersId).assertTextEquals("08f194ad3a5a5c1b")
        onNodeWithTag(MerchantDetailTestTags.AgentId).assertTextEquals("abcd1234")
    }

    @Test
    fun aBadLocationOrTimeStopsMeSavingUntilIFixIt() = runComposeUiTest {
        // Arrange — Given the form for a new merchant with a name
        showMerchants(newSource())
        waitForText("No merchants yet")
        onNodeWithTag(MerchantsTestTags.AddMerchantButton).performClick()
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Aldi")

        // Act — When I enter a latitude that is out of range and a time that isn't one
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextInput("123")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextInput("10")
        onNodeWithTag(MerchantEditTestTags.addRange(Weekday.Monday)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.openField(Weekday.Monday, 0)).performScrollTo().performTextInput("soon")

        // Assert — Then I'm told and can't save; fixing both lets me save
        onNodeWithTag(MerchantEditTestTags.CoordinatesError).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MerchantEditTestTags.HoursError).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextReplacement("51.5")
        onNodeWithTag(MerchantEditTestTags.openField(Weekday.Monday, 0)).performScrollTo().performTextReplacement("09:00")
        onNodeWithTag(MerchantEditTestTags.closeField(Weekday.Monday, 0)).performScrollTo().performTextInput("17:00")
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()
        waitForText("Aldi")
    }

    @Test
    fun editingAMerchantChangesItIncludingClearingItsLocation() = runComposeUiTest {
        // Arrange — Given a merchant with a location
        val source = newSource()
        runBlocking { source.save(butcher) }
        showMerchants(source)
        openMerchant("Hargreaves & Son")
        waitForText("12 Market Street")

        // Act — When I rename it through Edit and clear both coordinates
        onNodeWithTag(MerchantDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(MerchantDetailTestTags.EditMenuItem).performClick()
        onNodeWithTag(MerchantEditTestTags.NameField).performTextReplacement("Hargreaves Butchers")
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextReplacement("")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextReplacement("")
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert — Then the page shows the new name, the address stays, and the coordinates are gone
        waitForText("Hargreaves Butchers")
        onNodeWithTag(MerchantDetailTestTags.Address).assertTextEquals("12 Market Street")
        onNodeWithTag(MerchantDetailTestTags.Coordinates).assertDoesNotExist()
        assertNull(runBlocking { source.merchants.first().single().latitude })
    }

    @Test
    fun deletingAMerchantRemovesItAfterConfirming() = runComposeUiTest {
        // Arrange — Given a merchant
        val source = newSource()
        runBlocking { source.save(tesco) }
        showMerchants(source)
        openMerchant("Tesco Extra")

        // Act — When I delete it and confirm
        onNodeWithTag(MerchantDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(MerchantDetailTestTags.DeleteMenuItem).performClick()
        onNodeWithTag(MerchantDetailTestTags.ConfirmDelete).performClick()

        // Assert — Then I am back at an empty list
        waitForText("No merchants yet")
        assertEquals(emptyList(), runBlocking { source.merchants.first() })
    }

    @Test
    fun filteringByTypeAndSearchingNarrowsTheList() = runComposeUiTest {
        // Arrange — Given three merchants of different types
        val source = newSource()
        runBlocking { listOf(butcher, tesco, veg).forEach { source.save(it) } }
        showMerchants(source)
        waitForText("Tesco Extra")

        // Act / Assert — When I filter to national chains, only Tesco shows
        onNodeWithTag(MerchantsTestTags.typeChip(MerchantType.NationalChain)).performClick()
        onNodeWithText("Tesco Extra").assertIsDisplayed()
        onNodeWithText("Hargreaves & Son").assertDoesNotExist()

        // When I clear that and search for a service, only the merchant offering it shows
        onNodeWithTag(MerchantsTestTags.typeChip(MerchantType.NationalChain)).performClick()
        onNodeWithTag(MerchantsTestTags.SearchField).performTextInput("deli")
        onNodeWithText("Hargreaves & Son").assertIsDisplayed()
        onNodeWithText("Tesco Extra").assertDoesNotExist()

        // When I search by an address instead, the merchant at that address shows
        onNodeWithTag(MerchantsTestTags.SearchField).performTextReplacement("market street")
        onNodeWithText("Hargreaves & Son").assertIsDisplayed()
        onNodeWithText("Dave's Veg Stall").assertDoesNotExist()
    }

    @Test
    fun merchantsSurviveARestartAndReachAPairedDevice() = runComposeUiTest {
        // Arrange — Given a merchant saved on one device, whose document bytes were persisted
        var saved: ByteArray? = null
        val phone = newSource(persist = { saved = it })
        runBlocking { phone.save(butcher) }

        // Act — When the app restarts (the document is loaded from the saved bytes) and a paired tablet syncs
        val restarted = newSource(saved)
        val tablet = newSource()
        runBlocking { tablet.applyChanges(restarted.changesSince(ByteArray(0))) }
        showMerchants(tablet)

        // Assert — Then the tablet's list shows the merchant, and its page has the hours and location intact
        openMerchant("Hargreaves & Son")
        waitForText("09:00–17:00")
        onNodeWithTag(MerchantDetailTestTags.Coordinates).assertTextEquals("53.4808, -2.2426")
        assertEquals(butcher.hours, runBlocking { tablet.merchants.first().single().hours })
    }
}
