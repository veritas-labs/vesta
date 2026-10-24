package uk.veritaslabs.vesta.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        onNodeWithTag(ShoppingListTestTags.RemainingCounter).assertContentDescriptionEquals("1 of 4 items remaining")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theProgressBarSaysHowManyItemsTheFiltersHide() = runComposeUiTest {
        // Arrange
        val items = listOf(milk.copy(key = "a"), milk.copy(key = "b", got = 4), milk.copy(key = "c", got = 4))
        setContent { VestaTheme { ShoppingListScreen(items = items, hideChecked = true) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert — all three items still count, and the two hidden ones are noted
        onNodeWithTag(ShoppingListTestTags.RemainingCounter).assertContentDescriptionEquals("1 of 3 items remaining")
        onNodeWithTag(ShoppingListTestTags.HiddenNote).assertTextEquals("2 hidden by filters")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theProgressBarShowsNoHiddenNoteWhenNothingIsFiltered() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ShoppingListTestTags.HiddenNote).assertDoesNotExist()
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

    private val tescoBeans = ShoppingListItem("beans", "Tins", null, "Baked Beans", 1, "")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hideCheckedHidesCheckedOffItems() = runComposeUiTest {
        // Arrange
        val done = milk.copy(got = milk.quantity)
        setContent { VestaTheme { ShoppingListScreen(items = listOf(done, tescoBeans), hideChecked = true) } }

        // Act
        // (no user interaction — checking the filtered state)

        // Assert
        onNodeWithText("Baked Beans").assertIsDisplayed()
        onNodeWithText("Whole Milk").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSettingsIconOpensTheFilterScreen() = runComposeUiTest {
        // Arrange
        var opened = false
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onOpenFilter = { opened = true }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.FilterButton).performClick()

        // Assert
        assertEquals(true, opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theFilterScreenShowsAChipForEachTagAndReportsTaps() = runComposeUiTest {
        // Arrange
        var toggled: String? = null
        setContent {
            VestaTheme {
                ShoppingListFilterScreen(
                    tags = listOf("Butcher", "Farm shop"), selectedTags = emptySet(), onToggleTag = { toggled = it },
                    hideChecked = false, onHideCheckedChange = {},
                )
            }
        }

        // Act
        onNodeWithTag(ShoppingListTestTags.filterChip("Farm shop")).assertIsDisplayed()
        onNodeWithTag(ShoppingListTestTags.filterChip("Butcher")).performClick()

        // Assert
        assertEquals("Butcher", toggled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theFilterScreenSwitchReportsHideChecked() = runComposeUiTest {
        // Arrange
        var hide: Boolean? = null
        setContent {
            VestaTheme {
                ShoppingListFilterScreen(
                    tags = emptyList(), selectedTags = emptySet(), onToggleTag = {},
                    hideChecked = false, onHideCheckedChange = { hide = it },
                )
            }
        }

        // Act
        onNodeWithTag(ShoppingListFilterTestTags.HideCheckedSwitch).performClick()

        // Assert
        assertEquals(true, hide)
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun itemTagsAreThirteenSpAndReadableOnTheRowBackgroundInLightAndDark() = runComposeUiTest {
        // Arrange
        val tagged = milk.copy(tags = listOf("Farm shop"))
        var darkTheme by mutableStateOf(false)
        var tagColor = Color.Unspecified
        var surfaceColor = Color.Unspecified
        setContent {
            VestaTheme(darkTheme = darkTheme) {
                tagColor = MaterialTheme.colorScheme.onSurface
                // The row paints surfaceVariant at 40% alpha over the screen's surface (see ShoppingItemRow).
                surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                ShoppingListScreen(items = listOf(tagged))
            }
        }

        // Act
        val rendered = listOf(false, true).associateWith { dark ->
            darkTheme = dark
            waitForIdle()
            RenderedTag(tagTextStyle(tagged.key), tagColor, surfaceColor)
        }

        // Assert
        rendered.forEach { (dark, tag) ->
            assertEquals(13.sp, tag.style.fontSize, "tag font size (dark=$dark)")
            assertEquals(tag.expectedColor, tag.style.color, "tag colour (dark=$dark)")
            val ratio = contrastRatio(tag.expectedColor, tag.surface)
            assertTrue(ratio >= 7f, "tag contrast $ratio < 7 (dark=$dark)")
        }
    }

    private class RenderedTag(val style: TextStyle, val expectedColor: Color, val surface: Color)

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.tagTextStyle(itemKey: String): TextStyle {
        val node = onNodeWithTag(ShoppingListTestTags.itemTags(itemKey), useUnmergedTree = true).fetchSemanticsNode()
        val getLayout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!
        val layouts = mutableListOf<TextLayoutResult>()
        getLayout(layouts)
        return layouts.first().layoutInput.style
    }

    /** WCAG contrast ratio between two opaque colours. */
    private fun contrastRatio(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
