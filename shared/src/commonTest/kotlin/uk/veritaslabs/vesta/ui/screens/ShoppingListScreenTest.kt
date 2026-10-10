package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class ShoppingListScreenTest {

    private val milk = ShoppingListItem("whole milk", "Dairy", "Milk", "Whole Milk", 3, "2 pint")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theTopBarCountsItemsRemainingTreatingPartialAndOutOfStockAsCheckedOff() = runComposeUiTest {
        // Arrange
        val items = listOf(
            milk,
            milk.copy(key = "a", got = 3),
            milk.copy(key = "b", got = 1),
            milk.copy(key = "c", outOfStock = true),
        )
        setContent { VestaTheme { ShoppingListScreen(items = items) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ShoppingListTestTags.RemainingCounter).assertTextEquals("1 of 4 items remaining")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theTopBarShowsNoCounterForAnEmptyList() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ShoppingListTestTags.RemainingCounter).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachItemUnderItsCategoryWithItsQuantity() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Dairy").assertIsDisplayed()
        onNodeWithText("Milk").assertIsDisplayed()
        onNodeWithText("Whole Milk").assertIsDisplayed()
        onNodeWithText("×3").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAMessageWhenTheListIsEmpty() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Nothing to buy").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingARowTogglesItCrossedOff() = runComposeUiTest {
        // Arrange
        var toggled: ShoppingListItem? = null
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onToggleCrossedOff = { toggled = it }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performClick()

        // Assert
        assertEquals(milk, toggled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theOverflowMenuUnchecksAllItems() = runComposeUiTest {
        // Arrange
        var unchecked = false
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk.copy(got = 3)), onUncheckAll = { unchecked = true }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.OverflowButton).performClick()
        onNodeWithTag(ShoppingListTestTags.UncheckAllItem).performClick()

        // Assert
        assertEquals(true, unchecked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteAllInTheOverflowMenuDeletesOnlyAfterConfirming() = runComposeUiTest {
        // Arrange
        var deletedAll = 0
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onDeleteAll = { deletedAll++ }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.OverflowButton).performClick()
        onNodeWithTag(ShoppingListTestTags.DeleteAllItem).performClick()
        val deletedBeforeConfirming = deletedAll
        onNodeWithTag(ShoppingListTestTags.DeleteAllConfirmButton).performClick()

        // Assert
        assertEquals(0, deletedBeforeConfirming)
        assertEquals(1, deletedAll)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingTheDeleteAllDialogKeepsTheItems() = runComposeUiTest {
        // Arrange
        var deletedAll = false
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onDeleteAll = { deletedAll = true }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.OverflowButton).performClick()
        onNodeWithTag(ShoppingListTestTags.DeleteAllItem).performClick()
        onNodeWithTag(ShoppingListTestTags.DeleteAllCancelButton).performClick()

        // Assert
        assertEquals(false, deletedAll)
        onNodeWithTag(ShoppingListTestTags.DeleteAllConfirmButton).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPressingARowOpensTheSheetWithItsOptions() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performTouchInput { longClick() }

        // Assert
        onNodeWithTag(ShoppingListTestTags.OutOfStockButton).assertIsDisplayed()
        onNodeWithTag(ShoppingListTestTags.GotCount).assertTextEquals("0 of 3")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rightClickingARowOpensTheSheet() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performMouseInput { rightClick() }

        // Assert
        onNodeWithTag(ShoppingListTestTags.OutOfStockButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSheetRecordsAPartialCheckOffAndOutOfStock() = runComposeUiTest {
        // Arrange
        val calls = mutableListOf<Triple<String, Int, Boolean>>()
        setContent {
            VestaTheme {
                ShoppingListScreen(items = listOf(milk), onSetProgress = { key, got, oos -> calls += Triple(key, got, oos) })
            }
        }
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performTouchInput { longClick() }

        // Act
        onNodeWithTag(ShoppingListTestTags.GotIncrease).performClick()
        onNodeWithTag(ShoppingListTestTags.OutOfStockButton).performClick()

        // Assert
        assertEquals(listOf(Triple("whole milk", 1, false), Triple("whole milk", 0, true)), calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSheetEditButtonSelectsTheItem() = runComposeUiTest {
        // Arrange
        var selected: ShoppingListItem? = null
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onItemSelected = { selected = it }) } }
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performTouchInput { longClick() }

        // Act
        onNodeWithTag(ShoppingListTestTags.EditItemButton).performClick()

        // Assert
        assertEquals(milk, selected)
    }

    @Test
    fun anItemIsCrossedOffOnceAllOfItIsGotUnlessOutOfStock() {
        // Arrange
        val partly = milk.copy(got = 1)
        val all = milk.copy(got = 3)
        val missing = milk.copy(got = 3, outOfStock = true)

        // Act
        val states = listOf(milk, partly, all, missing).map { Triple(it.isCrossedOff, it.isPartlyGot, it.outOfStock) }

        // Assert
        assertEquals(
            listOf(Triple(false, false, false), Triple(false, true, false), Triple(true, false, false), Triple(false, false, true)),
            states,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theAddButtonAsksToAddAnItem() = runComposeUiTest {
        // Arrange
        var added = false
        setContent { VestaTheme { ShoppingListScreen(items = emptyList(), onAddItem = { added = true }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.AddItemButton).performClick()

        // Assert
        assertEquals(true, added)
    }

    private val farmMilk = ShoppingListItem("whole milk", "Dairy", null, "Whole Milk", 1, "", tags = listOf("Farm shop"))
    private val butcherMince = ShoppingListItem("mince", "Meat", null, "Beef Mince", 1, "", tags = listOf("Butcher"))

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAFilterChipForEachTagInUse() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(farmMilk, butcherMince)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ShoppingListTestTags.filterChip("Farm shop")).assertIsDisplayed()
        onNodeWithTag(ShoppingListTestTags.filterChip("Butcher")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAFilterChipReportsItsTag() = runComposeUiTest {
        // Arrange
        var toggled: String? = null
        setContent {
            VestaTheme { ShoppingListScreen(items = listOf(farmMilk, butcherMince), onToggleTag = { toggled = it }) }
        }

        // Act
        onNodeWithTag(ShoppingListTestTags.filterChip("Butcher")).performClick()

        // Assert
        assertEquals("Butcher", toggled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSelectedTagHidesItemsWithoutIt() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme { ShoppingListScreen(items = listOf(farmMilk, butcherMince), selectedTags = setOf("butcher")) }
        }

        // Act
        // (no user interaction — checking the filtered state)

        // Assert
        onNodeWithText("Beef Mince").assertIsDisplayed()
        onNodeWithText("Whole Milk").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsNoFilterChipsWhenNoItemHasATag() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ShoppingListTestTags.filterChip("Farm shop")).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theItemFormAddsATypedTagAndSavesIt() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(title = "Milk", primaryCategory = "Dairy", stockCount = 1),
                    labels = ShoppingItemFormLabels,
                    knownTags = emptyList(),
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.TagField).performTextInput("Farm shop")
        onNodeWithTag(PantryItemEditTestTags.TagAddButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf("Farm shop"), saved?.tags)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theItemFormOffersKnownTagsAndRemovesATappedOne() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(title = "Milk", primaryCategory = "Dairy", stockCount = 1, tags = listOf("Butcher")),
                    labels = ShoppingItemFormLabels,
                    knownTags = listOf("Butcher", "Farm shop"),
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.tagSuggestion("Farm shop")).performClick()
        onNodeWithTag(PantryItemEditTestTags.tag("Butcher")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf("Farm shop"), saved?.tags)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun thePantryFormHasNoTagsSection() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen() } }

        // Act
        // (no user interaction — checking the form's initial state)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.TagField).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theItemFormUsesQuantityWordingAndStartsAtOne() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(title = "Milk", primaryCategory = "Dairy", stockCount = 1),
                    labels = ShoppingItemFormLabels,
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.StockIncreaseButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        onNodeWithText("Add shopping item").assertIsDisplayed()
        assertEquals(2, saved?.stockCount)
    }

    private val pantry = listOf(
        PantryListItem("whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", 1, barcode = "123"),
        PantryListItem("oat milk", "Dairy", "Milk", "Oat Milk", "1 litre", 0),
        PantryListItem("bananas", "Fruit", null, "Bananas", "6 pack", 2),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typingANameSuggestsMatchingPantryItemsAndPickingOneFillsTheForm() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(labels = ShoppingItemFormLabels, suggestions = pantry, onSave = { saved = it })
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("mil")
        onNodeWithTag(PantryItemEditTestTags.suggestion("oat milk")).assertIsDisplayed()
        onNodeWithTag(PantryItemEditTestTags.suggestion("bananas")).assertDoesNotExist()
        onNodeWithTag(PantryItemEditTestTags.suggestion("whole milk")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(
            PantryItemDetails(
                primaryCategory = "Dairy", secondaryCategory = "Milk", title = "Whole Milk",
                packageSize = "2 pint", barcode = "123", stockCount = 1,
            ),
            saved,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noSuggestionsWhileEditingAnExistingItem() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(key = "k", primaryCategory = "Dairy", title = "Mil", stockCount = 1),
                    labels = ShoppingItemFormLabels,
                    suggestions = pantry,
                )
            }
        }

        // Act
        // (no user interaction — the name already matches Pantry items)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.suggestion("whole milk")).assertDoesNotExist()
    }

    @Test
    fun suggestionsListTitlesStartingWithTheTextFirstAndAreCapped() {
        // Arrange
        val many = (1..8).map { PantryListItem("k$it", "C", null, "Item $it", "", 0) } +
            PantryListItem("tea", "C", null, "Green Tea", "", 0) +
            PantryListItem("team", "C", null, "Team Snack", "", 0)

        // Act
        val result = suggestionsFor("te", many).map { it.title }

        // Assert
        assertEquals(listOf("Team Snack", "Item 1", "Item 2", "Item 3", "Item 4"), result)
    }
}
