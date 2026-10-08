package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
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
import uk.veritaslabs.vesta.ui.screens.PantryCategoryGroup
import uk.veritaslabs.vesta.ui.screens.PantryItemEditScreen
import uk.veritaslabs.vesta.ui.screens.PantryItemEditTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingItemFormLabels
import uk.veritaslabs.vesta.ui.screens.ShoppingListItem
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListTestTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.screens.shoppingTags
import uk.veritaslabs.vesta.toDetails
import uk.veritaslabs.vesta.toPantryDetails
import uk.veritaslabs.vesta.toShoppingDetails
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for shopping-list tags (see docs/testing/acceptance-tests.md): tag items by the shop they come
 * from, then filter the list to the shop you are standing in. Each test is one scenario, driven only through what a
 * user can see and tap, over the real Automerge-backed source, view model, list screen and item form.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ShoppingListTagsAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val milk = ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", tags = listOf("Farm shop"))
    private val sausages = ShoppingItemDetails(primaryCategory = "Meat", title = "Sausages", tags = listOf("Butcher"))
    private val beans = ShoppingItemDetails(primaryCategory = "Tins", title = "Baked Beans", tags = listOf("Supermarket"))
    private val salt = ShoppingItemDetails(primaryCategory = "Tins", title = "Salt")

    private fun newSource(
        bytes: ByteArray = newShoppingListDocument(),
        persist: suspend (ByteArray) -> Unit = {},
    ) = ShoppingListDocumentSource(
        Document.load(bytes), PantryDocumentSource(Document.load(newPantryDocument())), persist = persist,
    )

    /** The household's list as the screens show it: the list, and the item form reached by tapping a row or the add button. */
    private fun ComposeUiTest.showShoppingList(source: ShoppingListDocumentSource) {
        setContent {
            val viewModel = remember { ShoppingListViewModel(source) }
            var editing by remember { mutableStateOf<ShoppingItemDetails?>(null) }
            var formOpen by remember { mutableStateOf(false) }
            val items by viewModel.uiState.collectAsState()
            VestaTheme {
                if (formOpen) {
                    PantryItemEditScreen(
                        item = editing?.toPantryDetails(),
                        categories = listOf("Dairy", "Meat", "Tins").map { PantryCategoryGroup(it, emptyList()) },
                        labels = ShoppingItemFormLabels,
                        knownTags = shoppingTags(items.items),
                        onSave = { viewModel.save(it.toShoppingDetails()); formOpen = false },
                        onCancel = { formOpen = false },
                    )
                } else {
                    ShoppingListScreen(
                        viewModel,
                        Modifier,
                        onItemSelected = { item: ShoppingListItem -> editing = item.toDetails(); formOpen = true },
                        onAddItem = { editing = null; formOpen = true },
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.assertShown(vararg titles: String) = titles.forEach { onNodeWithText(it).assertIsDisplayed() }

    private fun ComposeUiTest.assertNotShown(vararg titles: String) =
        titles.forEach { onNodeWithText(it).assertDoesNotExist() }

    /** Long-presses the row titled [title] and taps Edit item in the sheet that opens. */
    private fun ComposeUiTest.openForEdit(title: String) {
        onNodeWithText(title).performTouchInput { longClick() }
        onNodeWithTag(ShoppingListTestTags.EditItemButton).performClick()
    }

    private fun ComposeUiTest.tapFilter(tag: String) = onNodeWithTag(ShoppingListTestTags.filterChip(tag)).performClick()

    @Test
    fun filteringToTheShopImStandingInShowsOnlyItemsPlannedForIt() = runComposeUiTest {
        // Arrange — Given a list with items for the farm shop, the butcher and the supermarket, and one with no shop
        val source = newSource()
        runBlocking { listOf(milk, sausages, beans, salt).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Whole Milk")

        // Act — When I filter to "Butcher"
        tapFilter("Butcher")

        // Assert — Then I see only the butcher's items
        assertShown("Sausages")
        assertNotShown("Whole Milk", "Baked Beans", "Salt")
    }

    @Test
    fun filteringToSeveralShopsShowsTheItemsForAnyOfThem() = runComposeUiTest {
        // Arrange — Given the same mixed list
        val source = newSource()
        runBlocking { listOf(milk, sausages, beans, salt).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Whole Milk")

        // Act — When I filter to "Butcher" and "Farm shop"
        tapFilter("Butcher")
        tapFilter("Farm shop")

        // Assert — Then I see both shops' items and nothing else
        assertShown("Sausages", "Whole Milk")
        assertNotShown("Baked Beans", "Salt")
    }

    @Test
    fun clearingTheFilterShowsTheWholeListAgain() = runComposeUiTest {
        // Arrange — Given I am filtered to "Butcher"
        val source = newSource()
        runBlocking { listOf(milk, sausages, beans, salt).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Whole Milk")
        tapFilter("Butcher")

        // Act — When I tap "Butcher" again
        tapFilter("Butcher")

        // Assert — Then everything is back
        assertShown("Whole Milk", "Sausages", "Baked Beans", "Salt")
    }

    @Test
    fun theFilterIsStillOnAfterEditingAnItemAndComingBack() = runComposeUiTest {
        // Arrange — Given I am filtered to "Butcher"
        val source = newSource()
        runBlocking { listOf(milk, sausages, beans).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Whole Milk")
        tapFilter("Butcher")

        // Act — When I open a butcher item and go back without changing it
        openForEdit("Sausages")
        onNodeWithTag(PantryItemEditTestTags.BackButton).performClick()

        // Assert — Then I am still looking at only the butcher's items
        assertShown("Sausages")
        assertNotShown("Whole Milk", "Baked Beans")
    }

    @Test
    fun tagsAddedWhenAddingAnItemMakeItAppearUnderThatShop() = runComposeUiTest {
        // Arrange — Given a list with only a farm shop item
        val source = newSource()
        runBlocking { source.save(milk) }
        showShoppingList(source)
        waitForText("Whole Milk")

        // Act — When I add "Beef Mince" tagged "Butcher" and filter to "Butcher"
        onNodeWithTag(ShoppingListTestTags.AddItemButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("Beef Mince")
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performClick()
        onNodeWithTag(PantryItemEditTestTags.categoryOption("Meat")).performClick()
        onNodeWithTag(PantryItemEditTestTags.TagField).performTextInput("Butcher")
        onNodeWithTag(PantryItemEditTestTags.TagAddButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()
        waitForText("Beef Mince")
        tapFilter("Butcher")

        // Assert — Then the mince is there and the milk is not
        assertShown("Beef Mince")
        assertNotShown("Whole Milk")
    }

    @Test
    fun aShopIUsedBeforeIsOfferedAsOneTapWhenTaggingAnotherItem() = runComposeUiTest {
        // Arrange — Given a list where "Farm shop" is already in use, and an untagged item
        val source = newSource()
        runBlocking { listOf(milk, salt).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Salt")

        // Act — When I open the untagged item and tap the "Farm shop" suggestion, then save
        openForEdit("Salt")
        onNodeWithTag(PantryItemEditTestTags.tagSuggestion("Farm shop")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { runBlocking { source.items.first() }.first { it.title == "Salt" }.tags.isNotEmpty() }
        tapFilter("Farm shop")

        // Assert — Then both farm shop items show
        assertShown("Whole Milk", "Salt")
    }

    @Test
    fun removingAnItemsTagTakesItOutOfThatShopsFilter() = runComposeUiTest {
        // Arrange — Given two butcher items
        val source = newSource()
        val mince = ShoppingItemDetails(primaryCategory = "Meat", title = "Beef Mince", tags = listOf("Butcher"))
        runBlocking { listOf(sausages, mince).forEach { source.save(it) } }
        showShoppingList(source)
        waitForText("Sausages")

        // Act — When I remove the "Butcher" tag from the sausages and filter to "Butcher"
        openForEdit("Sausages")
        onNodeWithTag(PantryItemEditTestTags.tag("Butcher")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { runBlocking { source.items.first() }.first { it.title == "Sausages" }.tags.isEmpty() }
        tapFilter("Butcher")

        // Assert — Then only the mince shows
        assertShown("Beef Mince")
        assertNotShown("Sausages")
    }

    @Test
    fun tagsSurviveClosingAndReopeningTheApp() = runComposeUiTest {
        // Arrange — Given a tagged list that was saved, and the app opened again from what was saved
        var savedDocument = newShoppingListDocument()
        val first = newSource(persist = { savedDocument = it })
        runBlocking { listOf(milk, sausages).forEach { first.save(it) } }
        showShoppingList(newSource(savedDocument))
        waitForText("Whole Milk")

        // Act — When I filter to "Farm shop"
        tapFilter("Farm shop")

        // Assert — Then the tagged items are still found by their tag
        assertShown("Whole Milk")
        assertNotShown("Sausages")
        assertEquals(listOf("Farm shop"), runBlocking { newSource(savedDocument).items.first() }.first { it.title == "Whole Milk" }.tags)
    }
}
