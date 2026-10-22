package uk.veritaslabs.vesta.acceptance

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
import uk.veritaslabs.vesta.automerge.ensurePantryItem
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListFilterScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListFilterTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the shopping list's filter screen (see docs/testing/acceptance-tests.md).
 * Each test is one scenario, driven only through what a user can see and tap, over the real Automerge-backed Pantry and
 * shopping list, the view model and both screens. The criteria:
 *
 *  1. The settings icon opens a filter screen with a chip for each tag; choosing a tag narrows the list.
 *  2. The filter screen's "Hide checked items" switch hides items that are checked off, and showing them again works.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListFiltersAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val milk = ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", tags = listOf("Weekly"))
    private val sausages = ShoppingItemDetails(primaryCategory = "Meat", title = "Sausages")
    private val beans = ShoppingItemDetails(primaryCategory = "Tins", title = "Baked Beans", tags = listOf("Weekly"))

    private class Lists(val pantry: PantryDocumentSource, val shopping: ShoppingListDocumentSource)

    /** A list of milk and beans (both tagged "Weekly") and sausages. */
    private fun newLists(): Lists {
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        val shopping = ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
        runBlocking {
            // The products are already in the Pantry (bought before)
            listOf(milk, sausages, beans).forEach { item ->
                pantry.mutate { doc ->
                    ensurePantryItem(doc, item.primaryCategory, item.secondaryCategory, item.title, item.packageSize, item.barcode)
                }
                shopping.save(item)
            }
        }
        return Lists(pantry, shopping)
    }

    private fun ComposeUiTest.show(lists: Lists) {
        setContent {
            val viewModel = remember { ShoppingListViewModel(lists.shopping) }
            var filterOpen by remember { mutableStateOf(false) }
            VestaTheme {
                if (filterOpen) {
                    ShoppingListFilterScreen(viewModel, onBack = { filterOpen = false })
                } else {
                    ShoppingListScreen(viewModel, onOpenFilter = { filterOpen = true })
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
