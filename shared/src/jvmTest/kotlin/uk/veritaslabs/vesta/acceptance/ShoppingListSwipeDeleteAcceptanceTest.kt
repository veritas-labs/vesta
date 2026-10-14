package uk.veritaslabs.vesta.acceptance

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import uk.veritaslabs.vesta.ui.components.LocalUndo
import uk.veritaslabs.vesta.ui.components.UndoController
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for swipe-to-delete on the shopping list: swiping a row either way deletes the item, revealing a
 * red background with a trash can underneath as it slides. Driven through the list screen over the real
 * Automerge-backed source.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListSwipeDeleteAcceptanceTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newSource() = ShoppingListDocumentSource(
        Document.load(newShoppingListDocument()), PantryDocumentSource(Document.load(newPantryDocument())),
    )

    private suspend fun ShoppingListDocumentSource.seed() {
        save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", quantity = 4))
        save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Brie", quantity = 1))
    }

    private fun ComposeUiTest.show(source: ShoppingListDocumentSource) {
        setContent {
            val viewModel = remember { ShoppingListViewModel(source) }
            val snackbars = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val undo = remember { UndoController(snackbars, scope, "Undo") }
            CompositionLocalProvider(LocalUndo provides undo) {
                VestaTheme {
                    Box {
                        ShoppingListScreen(viewModel)
                        SnackbarHost(snackbars)
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun titles(source: ShoppingListDocumentSource) = runBlocking { source.items.first().map { it.title } }

    @Test
    fun swipingAnItemRightDeletesIt() = runComposeUiTest {
        // Arrange — Given a list with milk and brie
        val source = newSource().also { runBlocking { it.seed() } }
        show(source)

        // Act — When the milk row is swiped to the right
        onNodeWithTag(ShoppingListTestTags.swipeToDelete("whole milk")).performTouchInput { swipeRight() }

        // Assert — Then milk is deleted and brie stays
        waitUntil(timeoutMillis = 5_000) { titles(source) == listOf("Brie") }
        assertEquals(listOf("Brie"), titles(source))
    }

    @Test
    fun swipingAnItemLeftDeletesIt() = runComposeUiTest {
        // Arrange — Given a list with milk and brie
        val source = newSource().also { runBlocking { it.seed() } }
        show(source)

        // Act — When the milk row is swiped to the left
        onNodeWithTag(ShoppingListTestTags.swipeToDelete("whole milk")).performTouchInput { swipeLeft() }

        // Assert — Then milk is deleted and brie stays
        waitUntil(timeoutMillis = 5_000) { titles(source) == listOf("Brie") }
        assertEquals(listOf("Brie"), titles(source))
    }

    @Test
    fun swipingAwayAnItemCanBeUndoneAndKeepsWhatWasPickedUp() = runComposeUiTest {
        // Arrange — Given milk with 2 of 4 picked up, and brie
        val source = newSource().also { runBlocking { it.seed(); it.setProgress("whole milk", 2, false) } }
        show(source)

        // Act — When I swipe the milk away and press Undo
        onNodeWithTag(ShoppingListTestTags.swipeToDelete("whole milk")).performTouchInput { swipeRight() }
        waitUntil(timeoutMillis = 5_000) { titles(source) == listOf("Brie") }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()

        // Assert — Then the milk is back with its quantity and the 2 already got
        waitUntil(timeoutMillis = 5_000) { runBlocking { source.items.first() }.singleOrNull { it.title == "Whole Milk" }?.got == 2 }
        val milk = runBlocking { source.items.first() }.single { it.title == "Whole Milk" }
        assertEquals(4, milk.quantity)
        assertEquals(2, milk.got)
    }
}
