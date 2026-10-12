package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.screens.MerchantLinkChange
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.PantryContents
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListFilterScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListFilterTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the shopping list's merchant chips and filter screen (see docs/testing/acceptance-tests.md).
 * Each test is one scenario, driven only through what a user can see and tap, over the real Automerge-backed Pantry and
 * shopping list, the view model and both screens. The criteria:
 *
 *  1. The list shows a chip for each merchant that some item is bought from (set on the item's Pantry entry).
 *  2. Switching merchants on shows only the items bought from one of them; switching them off shows everything.
 *  3. The settings icon opens a filter screen with a chip for each tag; choosing a tag narrows the list.
 *  4. The filter screen's "Hide checked items" switch hides items that are checked off, and showing them again works.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListFiltersAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val merchants = listOf(Merchant(key = "tesco", name = "Tesco Extra"), Merchant(key = "butcher", name = "Hargreaves & Son"))
    private val milk = ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", tags = listOf("Weekly"))
    private val sausages = ShoppingItemDetails(primaryCategory = "Meat", title = "Sausages")
    private val beans = ShoppingItemDetails(primaryCategory = "Tins", title = "Baked Beans", tags = listOf("Weekly"))

    private class Lists(val pantry: PantryDocumentSource, val shopping: ShoppingListDocumentSource)

    /** A list of milk and beans (bought at Tesco) and sausages (bought at the butcher's). */
    private fun newLists(): Lists {
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        val shopping = ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
        runBlocking {
            listOf(milk, sausages, beans).forEach { shopping.save(it) }
            pantry.changeMerchants(setOf("whole milk", "baked beans"), MerchantLinkChange.Replace(listOf("tesco")))
            pantry.changeMerchants(setOf("sausages"), MerchantLinkChange.Replace(listOf("butcher")))
        }
        return Lists(pantry, shopping)
    }

    private fun ComposeUiTest.show(lists: Lists) {
        setContent {
            val viewModel = remember { ShoppingListViewModel(lists.shopping) }
            val pantry by lists.pantry.contents.collectAsState(PantryContents())
            var filterOpen by remember { mutableStateOf(false) }
            VestaTheme {
                if (filterOpen) {
                    ShoppingListFilterScreen(viewModel, onBack = { filterOpen = false })
                } else {
                    ShoppingListScreen(viewModel, onOpenFilter = { filterOpen = true }, merchants = merchants, pantryItems = pantry.items)
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.assertShown(vararg titles: String) = titles.forEach { onNodeWithText(it).assertIsDisplayed() }

    private fun ComposeUiTest.assertNotShown(vararg titles: String) = titles.forEach { onNodeWithText(it).assertDoesNotExist() }

    private fun ComposeUiTest.openFilterScreen() = onNodeWithTag(ShoppingListTestTags.FilterButton).performClick()

    private fun ComposeUiTest.closeFilterScreen() = onNodeWithTag(ShoppingListFilterTestTags.BackButton).performClick()

    @Test
    fun switchingOnTheButchersShowsOnlyWhatIBuyThere() = runComposeUiTest {
        // Arrange — Given a list where milk and beans come from Tesco and sausages from the butcher
        show(newLists())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Sausages").fetchSemanticsNodes().isNotEmpty() }

        // Act — When I switch on the butcher
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagExists(ShoppingListTestTags.merchantChip("butcher")) }
        onNodeWithTag(ShoppingListTestTags.merchantChip("butcher")).performClick()

        // Assert — Then I see only the sausages
        assertShown("Sausages")
        assertNotShown("Whole Milk", "Baked Beans")
    }

    @Test
    fun switchingTheMerchantOffAgainShowsTheWholeList() = runComposeUiTest {
        // Arrange — Given I am looking only at what the butcher sells
        show(newLists())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagExists(ShoppingListTestTags.merchantChip("butcher")) }
        onNodeWithTag(ShoppingListTestTags.merchantChip("butcher")).performClick()

        // Act — When I tap the butcher again
        onNodeWithTag(ShoppingListTestTags.merchantChip("butcher")).performClick()

        // Assert — Then everything is back
        assertShown("Whole Milk", "Sausages", "Baked Beans")
    }

    @Test
    fun theFilterScreenNarrowsTheListByTag() = runComposeUiTest {
        // Arrange — Given a list where milk and beans are tagged "Weekly"
        show(newLists())

        // Act — When I open the filter screen, choose "Weekly" and go back
        openFilterScreen()
        onNodeWithTag(ShoppingListTestTags.filterChip("Weekly")).performClick()
        closeFilterScreen()

        // Assert — Then the sausages are gone
        assertShown("Whole Milk", "Baked Beans")
        assertNotShown("Sausages")
    }

    @Test
    fun hidingCheckedItemsRemovesWhatIHaveBoughtAndShowingThemBringsThemBack() = runComposeUiTest {
        // Arrange — Given I have crossed off the milk
        show(newLists())
        onNodeWithText("Whole Milk").performClick()

        // Act — When I turn on "Hide checked items" and go back
        openFilterScreen()
        onNodeWithTag(ShoppingListFilterTestTags.HideCheckedSwitch).performClick()
        closeFilterScreen()

        // Assert — Then the milk is hidden and the rest remain
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isEmpty() }
        assertShown("Sausages", "Baked Beans")

        // Act — When I turn it off again
        openFilterScreen()
        onNodeWithTag(ShoppingListFilterTestTags.HideCheckedSwitch).performClick()
        closeFilterScreen()

        // Assert — Then the milk is back
        assertShown("Whole Milk")
    }

    private fun ComposeUiTest.onAllNodesWithTagExists(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
