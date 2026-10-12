package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantLinkTestTags
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.PantryItemEditScreen
import uk.veritaslabs.vesta.ui.screens.PantryItemEditTestTags
import uk.veritaslabs.vesta.ui.screens.PantryScreen
import uk.veritaslabs.vesta.ui.screens.PantryTestTags
import uk.veritaslabs.vesta.ui.screens.PantryViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for linking Merchants to Pantry items (docs/adr/ADR-026-merchants.md, "Linking to the Pantry"; see
 * docs/testing/acceptance-tests.md). Each test is one scenario, driven only through what a user can see and tap, over the
 * real Automerge-backed Pantry, the view model and the Pantry and item screens. The criteria:
 *
 *  1. An item can be bought from several merchants, kept in priority order, and I can change that order on its form.
 *  2. The Pantry lists each item's merchants in that order.
 *  3. I can select several items and add merchants to all of them, after any they already have.
 *  4. I can select several items and remove merchants from all of them.
 *  5. I can select several items and replace their merchants with one ordered list.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PantryMerchantsAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val merchants = listOf(
        Merchant(key = "tesco", name = "Tesco Extra"),
        Merchant(key = "butcher", name = "Hargreaves & Son"),
        Merchant(key = "stall", name = "Dave's Veg Stall"),
    )

    private fun newPantry(): PantryDocumentSource = PantryDocumentSource(Document.load(newPantryDocument()))

    private fun item(title: String, merchantKeys: List<String>? = null) =
        PantryItemDetails(primaryCategory = "Dairy", title = title, merchantKeys = merchantKeys)

    private fun androidx.compose.ui.test.ComposeUiTest.showPantry(source: PantryDocumentSource) {
        setContent {
            val viewModel = androidx.compose.runtime.remember { PantryViewModel(source) }
            VestaTheme { PantryScreen(viewModel, merchants = merchants) }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForBuyFrom(key: String, text: String) = waitUntil(timeoutMillis = 5_000) {
        runCatching { onNodeWithTag(PantryTestTags.buyFrom(key), useUnmergedTree = true).assertTextEquals(text) }.isSuccess
    }

    private fun PantryDocumentSource.merchantsOf(key: String) = runBlocking { contents.first().items.first { it.key == key }.merchantKeys }

    @Test
    fun anItemsMerchantsAreEditedInPriorityOrderOnItsForm() = runComposeUiTest {
        // Arrange — Given a milk item bought from Tesco, and its form open
        val source = newPantry()
        runBlocking { source.save(item("Milk", listOf("tesco"))) }
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(key = "milk", primaryCategory = "Dairy", title = "Milk", merchantKeys = listOf("tesco")),
                    merchants = merchants,
                    onSave = { runBlocking { source.save(it) } },
                )
            }
        }

        // Act — When I add the butcher, move it above Tesco and save
        onNodeWithTag(MerchantLinkTestTags.add("butcher")).performScrollTo().performClick()
        onNodeWithTag(MerchantLinkTestTags.moveUp("butcher")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert — Then the item is bought from the butcher first, then Tesco
        assertEquals(listOf("butcher", "tesco"), source.merchantsOf("milk"))
    }

    @Test
    fun thePantryListsEachItemsMerchantsInPriorityOrder() = runComposeUiTest {
        // Arrange — Given milk bought from the stall, then Tesco
        val source = newPantry()
        runBlocking { source.save(item("Milk", listOf("stall", "tesco"))) }

        // Act — When I open the Pantry
        showPantry(source)

        // Assert — Then milk shows "Buy from: Dave's Veg Stall, Tesco Extra"
        waitForBuyFrom("milk", "Buy from: Dave's Veg Stall, Tesco Extra")
    }

    @Test
    fun addingMerchantsToSeveralItemsAppendsAfterTheirExistingOnes() = runComposeUiTest {
        // Arrange — Given milk (from Tesco), eggs (none) and bread
        val source = newPantry()
        runBlocking {
            source.save(item("Milk", listOf("tesco")))
            source.save(item("Eggs"))
            source.save(item("Bread"))
        }
        showPantry(source)
        waitForTag(PantryTestTags.pantryItem("milk"))

        // Act — When I select milk and eggs and add the butcher and Tesco
        onNodeWithTag(PantryTestTags.pantryItem("milk")).performTouchInput { longClick() }
        onNodeWithTag(PantryTestTags.pantryItem("eggs")).performClick()
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("butcher")).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert — Then milk keeps Tesco first and gains the butcher, eggs gets both in the order chosen, bread is untouched
        waitForBuyFrom("eggs", "Buy from: Hargreaves & Son, Tesco Extra")
        waitForBuyFrom("milk", "Buy from: Tesco Extra, Hargreaves & Son")
        assertEquals(emptyList(), source.merchantsOf("bread"))
    }

    @Test
    fun removingAMerchantFromSeveralItemsLeavesTheirOtherMerchants() = runComposeUiTest {
        // Arrange — Given milk and eggs both bought from Tesco then the butcher
        val source = newPantry()
        runBlocking {
            source.save(item("Milk", listOf("tesco", "butcher")))
            source.save(item("Eggs", listOf("tesco", "butcher")))
        }
        showPantry(source)
        waitForTag(PantryTestTags.pantryItem("milk"))

        // Act — When I select both and remove Tesco
        onNodeWithTag(PantryTestTags.pantryItem("milk")).performTouchInput { longClick() }
        onNodeWithTag(PantryTestTags.pantryItem("eggs")).performClick()
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()
        onNodeWithTag(MerchantLinkTestTags.ModeRemove).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert — Then both are bought from the butcher only
        waitForBuyFrom("milk", "Buy from: Hargreaves & Son")
        waitForBuyFrom("eggs", "Buy from: Hargreaves & Son")
    }

    @Test
    fun replacingTheMerchantsOfSeveralItemsGivesThemOneOrderedList() = runComposeUiTest {
        // Arrange — Given milk (from Tesco) and eggs (from the butcher)
        val source = newPantry()
        runBlocking {
            source.save(item("Milk", listOf("tesco")))
            source.save(item("Eggs", listOf("butcher")))
        }
        showPantry(source)
        waitForTag(PantryTestTags.pantryItem("milk"))

        // Act — When I select both and replace their merchants with the stall, then Tesco
        onNodeWithTag(PantryTestTags.pantryItem("milk")).performTouchInput { longClick() }
        onNodeWithTag(PantryTestTags.pantryItem("eggs")).performClick()
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()
        onNodeWithTag(MerchantLinkTestTags.ModeReplace).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("stall")).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert — Then both are bought from the stall first, then Tesco
        waitForBuyFrom("milk", "Buy from: Dave's Veg Stall, Tesco Extra")
        waitForBuyFrom("eggs", "Buy from: Dave's Veg Stall, Tesco Extra")
    }
}
