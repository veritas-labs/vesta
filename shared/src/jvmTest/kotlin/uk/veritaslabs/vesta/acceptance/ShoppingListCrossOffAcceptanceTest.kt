package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for crossing off shopping-list items: tap a row to cross it off (and again to undo), long-press it
 * to say the shop was out of it or that only some were got. Driven through the list screen and its bottom sheet over
 * the real Automerge-backed source, including a reload from the saved bytes.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListCrossOffAcceptanceTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val milk = ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", quantity = 4)

    private fun newSource(
        bytes: ByteArray = newShoppingListDocument(),
        persist: suspend (ByteArray) -> Unit = {},
    ) = ShoppingListDocumentSource(
        Document.load(bytes), PantryDocumentSource(Document.load(newPantryDocument())), persist = persist,
    )

    private fun ComposeUiTest.show(source: ShoppingListDocumentSource) {
        setContent {
            val viewModel = remember { ShoppingListViewModel(source) }
            VestaTheme { ShoppingListScreen(viewModel) }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.row() = onNodeWithTag(ShoppingListTestTags.item("whole milk"))

    private fun ComposeUiTest.openSheet() = row().performTouchInput { longClick() }

    private fun ComposeUiTest.waitForSaved(source: ShoppingListDocumentSource, condition: (Int, Boolean) -> Boolean) =
        waitUntil(timeoutMillis = 5_000) {
            runBlocking { source.items.first().single() }.let { condition(it.got, it.outOfStock) }
        }

    @Test
    fun tappingAnItemCrossesItOffAndTappingAgainPutsItBack() = runComposeUiTest {
        // Arrange — Given a list with 4 pints of milk to buy
        val source = newSource().also { runBlocking { it.save(milk) } }
        show(source)

        // Act — When I tap the milk
        row().performClick()
        waitForSaved(source) { got, _ -> got == 4 }
        val crossedOff = runBlocking { source.items.first().single().isCrossedOff }
        row().performClick()
        waitForSaved(source) { got, _ -> got == 0 }

        // Assert — Then it is crossed off, and tapping again makes it normal
        assertEquals(true, crossedOff)
        assertEquals(false, runBlocking { source.items.first().single().isCrossedOff })
    }

    @Test
    fun longPressingLetsMeCheckOffPartOfAnItem() = runComposeUiTest {
        // Arrange — Given 4 pints of milk to buy
        val source = newSource().also { runBlocking { it.save(milk) } }
        show(source)

        // Act — When I long-press it and add two with the plus button
        openSheet()
        onNodeWithTag(ShoppingListTestTags.GotIncrease).performClick()
        onNodeWithTag(ShoppingListTestTags.GotIncrease).performClick()
        waitForSaved(source) { got, _ -> got == 2 }

        // Assert — Then the sheet and the list show 2 of 4, and it is not crossed off
        onNodeWithTag(ShoppingListTestTags.GotCount).assertTextEquals("2 of 4")
        val item = runBlocking { source.items.first().single() }
        assertEquals(Pair(true, false), Pair(item.isPartlyGot, item.isCrossedOff))
    }

    @Test
    fun markingAnItemOutOfStockShowsItAsSuchAndSurvivesAReload() = runComposeUiTest {
        // Arrange — Given 4 pints of milk to buy
        var saved = newShoppingListDocument()
        val source = newSource(persist = { saved = it }).also { runBlocking { it.save(milk) } }
        show(source)

        // Act — When I long-press it and tap "Item out of stock"
        openSheet()
        onNodeWithTag(ShoppingListTestTags.OutOfStockButton).performClick()
        waitForSaved(source) { _, outOfStock -> outOfStock }

        // Assert — Then the row reads "Out of stock", and a list reloaded from the saved bytes still has it so
        onNodeWithText("Out of stock").assertExists()
        assertEquals(true, runBlocking { newSource(saved).items.first().single().outOfStock })
    }
}
