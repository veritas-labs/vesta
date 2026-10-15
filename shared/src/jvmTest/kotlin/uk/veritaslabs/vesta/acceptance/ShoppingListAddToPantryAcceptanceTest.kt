package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.screens.AddedToPantryScreen
import uk.veritaslabs.vesta.ui.screens.AddedToPantryTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for "Add checked items to Pantry" in the shopping list's overflow menu: what was picked up goes
 * into the Pantry's stock, the list keeps what is still wanted, and a celebration screen with a Close button confirms
 * it. Driven through the list screen over the real Automerge-backed sources.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListAddToPantryAcceptanceTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
    private var saved = newShoppingListDocument()
    private val source = ShoppingListDocumentSource(Document.load(saved), pantry, persist = { saved = it })

    private suspend fun seed() {
        source.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", quantity = 4))
        source.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Brie", quantity = 3))
        source.save(ShoppingItemDetails(primaryCategory = "Meat", title = "Sausages", quantity = 1))
        source.save(ShoppingItemDetails(primaryCategory = "Fruit", title = "Bananas", quantity = 2))
        source.setProgress("whole milk", got = 4, outOfStock = false)
        source.setProgress("brie", got = 1, outOfStock = false)
        source.setProgress("sausages", got = 0, outOfStock = true)
    }

    private fun stock(title: String) = runBlocking { pantry.contents.first().items.first { it.title == title }.stockCount }

    private fun inPantry(title: String) = runBlocking { pantry.contents.first().items.any { it.title == title } }

    private fun listed() = runBlocking { source.items.first() }

    private fun ComposeUiTest.show() {
        setContent {
            val viewModel = remember { ShoppingListViewModel(source) }
            var addedCount by remember { mutableStateOf<Int?>(null) }
            VestaTheme {
                val count = addedCount
                if (count == null) {
                    ShoppingListScreen(viewModel, onAddedToPantry = { addedCount = it })
                } else {
                    AddedToPantryScreen(addedCount = count, onClose = { addedCount = null })
                }
            }
        }
    }

    private fun ComposeUiTest.openMenu() = onNodeWithTag(ShoppingListTestTags.OverflowButton).performClick()

    @Test
    fun givenCheckedItems_whenAddingThemToPantry_thenTheirStockGoesUpAndTheCelebrationShows() {
        runComposeUiTest {
            // Arrange
            runBlocking { seed() }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }

            // Act
            openMenu()
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(AddedToPantryTestTags.CloseButton).fetchSemanticsNodes().isNotEmpty() }

            // Assert
            onNodeWithText("Added to your Pantry").assertExists()
            onNodeWithText("2 items are now in your Pantry.").assertExists()
            onNodeWithTag(AddedToPantryTestTags.Animation).assertExists()
            assertEquals(4, stock("Whole Milk"))
            assertEquals(1, stock("Brie"))
            assertEquals(false, inPantry("Sausages"))
            assertEquals(false, inPantry("Bananas"))
        }
    }

    @Test
    fun givenCheckedItems_whenAddingThemToPantry_thenBoughtItemsLeaveTheListAndPartlyGotOnesKeepWhatIsLeft() {
        runComposeUiTest {
            // Arrange
            runBlocking { seed() }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }

            // Act
            openMenu()
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(AddedToPantryTestTags.CloseButton).fetchSemanticsNodes().isNotEmpty() }

            // Assert
            val byTitle = listed().associateBy { it.title }
            assertEquals(setOf("Brie", "Sausages", "Bananas"), byTitle.keys)
            assertEquals(2, byTitle.getValue("Brie").quantity)
            assertEquals(0, byTitle.getValue("Brie").got)
            assertEquals(true, byTitle.getValue("Sausages").outOfStock)
        }
    }

    @Test
    fun givenItemsAddedToPantry_whenTheListIsReloadedFromItsSavedBytes_thenTheyAreStillOffIt() {
        runComposeUiTest {
            // Arrange
            runBlocking { seed() }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }
            openMenu()
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(AddedToPantryTestTags.CloseButton).fetchSemanticsNodes().isNotEmpty() }

            // Act
            val reloaded = ShoppingListDocumentSource(Document.load(saved), pantry)

            // Assert
            val byTitle = runBlocking { reloaded.items.first() }.associateBy { it.title }
            assertEquals(setOf("Brie", "Sausages", "Bananas"), byTitle.keys)
            assertEquals(2, byTitle.getValue("Brie").quantity)
        }
    }

    @Test
    fun givenTheCelebration_whenTappingClose_thenTheShoppingListShowsAgain() {
        runComposeUiTest {
            // Arrange
            runBlocking { seed() }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }
            openMenu()
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(AddedToPantryTestTags.CloseButton).fetchSemanticsNodes().isNotEmpty() }

            // Act
            onNodeWithTag(AddedToPantryTestTags.CloseButton).performClick()

            // Assert
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Brie").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Whole Milk").assertDoesNotExist()
        }
    }

    @Test
    fun givenNothingIsCheckedOff_thenAddCheckedItemsToPantryIsDisabled() {
        runComposeUiTest {
            // Arrange
            runBlocking { source.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", quantity = 4)) }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }

            // Act
            openMenu()

            // Assert
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).assertIsNotEnabled()
        }
    }

    @Test
    fun givenOnlyAnOutOfStockItem_thenAddCheckedItemsToPantryIsDisabledButItsEnabledOnceSomethingIsGot() {
        runComposeUiTest {
            // Arrange
            runBlocking {
                source.save(ShoppingItemDetails(primaryCategory = "Meat", title = "Sausages", quantity = 1))
                source.setProgress("sausages", got = 0, outOfStock = true)
            }
            show()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Sausages").fetchSemanticsNodes().isNotEmpty() }
            openMenu()
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).assertIsNotEnabled()

            // Act
            runBlocking { source.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Brie", quantity = 1)) }
            runBlocking { source.setProgress("brie", got = 1, outOfStock = false) }
            waitForIdle()

            // Assert
            onNodeWithTag(ShoppingListTestTags.AddCheckedToPantryItem).assertIsEnabled()
        }
    }
}
