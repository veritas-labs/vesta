package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class PantryItemEditScreenTest {

    private val existingItem = PantryItemDetails(
        key = "5000157024671",
        primaryCategory = "Condiments",
        secondaryCategory = "Ketchup",
        title = "Heinz Ketchup",
        packageSize = "500ml",
        barcode = "5000157024671",
        stockCount = 4,
    )

    private val categories = listOf(
        PantryCategoryGroup("Condiments", listOf("Ketchup", "Mayonnaise")),
        PantryCategoryGroup("Fruit & Vegetables", listOf("Fruit")),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addingANewItemHasNoOverflowMenuAndSaveIsDisabledUntilRequiredFieldsAreFilled() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen() } }

        // Act
        // (no user interaction yet — checking the screen's initial state)

        // Assert
        onAllNodesWithTag(PantryItemEditTestTags.OverflowButton).assertCountEquals(0)
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fillingInTheRequiredFieldsAndSavingInvokesOnSaveWithTheTrimmedValues() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme { PantryItemEditScreen(categories = categories, onSave = { saved = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput(" Bananas ")
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performClick()
        onNodeWithTag(PantryItemEditTestTags.categoryOption("Fruit & Vegetables")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().assertIsEnabled().performClick()

        // Assert
        assertEquals("Bananas", saved?.title)
        assertEquals("Fruit & Vegetables", saved?.primaryCategory)
        assertEquals("", saved?.packageSize) // loose: no package size chosen
        assertNull(saved?.key)
        assertNull(saved?.secondaryCategory)
        assertNull(saved?.barcode)
        assertEquals(0, saved?.stockCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editingAnExistingItemPrefillsItsFieldsAndShowsTheOverflowMenu() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = existingItem) } }

        // Act
        // (no user interaction — checking the screen's initial, pre-filled state)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.OverflowButton).assertIsDisplayed()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun choosingDeleteFromTheOverflowMenuInvokesOnDeleteWithTheItemsKey() = runComposeUiTest {
        // Arrange
        var deletedKey: String? = null
        setContent {
            VestaTheme { PantryItemEditScreen(item = existingItem, onDelete = { deletedKey = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.OverflowButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.DeleteMenuItem).performClick()

        // Assert
        assertEquals("5000157024671", deletedKey)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingThePackageSizeRowHandsTheFormAsTypedToOnEditPackage() = runComposeUiTest {
        // Arrange
        var draft: PantryItemDetails? = null
        setContent {
            VestaTheme { PantryItemEditScreen(item = existingItem, onEditPackage = { draft = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("!")
        onNodeWithTag(PantryItemEditTestTags.PackageSizeField).performScrollTo().performClick()

        // Assert
        assertEquals("!Heinz Ketchup", draft?.title)
        assertEquals("500ml", draft?.packageSize)
        assertEquals(existingItem.key, draft?.key)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anItemWithNoPackageSizeCanBeSavedAsLoose() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = existingItem.copy(packageSize = ""),
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().assertIsEnabled().performClick()

        // Assert
        assertEquals("", saved?.packageSize)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnCancel() = runComposeUiTest {
        // Arrange
        var cancelled = false
        setContent { VestaTheme { PantryItemEditScreen(onCancel = { cancelled = true }) } }

        // Act
        onNodeWithTag(PantryItemEditTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, cancelled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun secondaryDropdownOffersOnlyTheChosenPrimarysSubcategoriesAndSavesTheChoice() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme { PantryItemEditScreen(categories = categories, onSave = { saved = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("Mayo")
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performClick()
        onNodeWithTag(PantryItemEditTestTags.categoryOption("Condiments")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SecondaryCategoryField).performClick()
        val offersFruit = onAllNodesWithTag(PantryItemEditTestTags.categoryOption("Fruit")).fetchSemanticsNodes().isNotEmpty()
        onNodeWithTag(PantryItemEditTestTags.categoryOption("Mayonnaise")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals(false, offersFruit)
        assertEquals("Condiments", saved?.primaryCategory)
        assertEquals("Mayonnaise", saved?.secondaryCategory)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun changingThePrimaryClearsASecondaryThatNoLongerApplies() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(item = existingItem, categories = categories, onSave = { saved = it })
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performClick()
        onNodeWithTag(PantryItemEditTestTags.categoryOption("Fruit & Vegetables")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals("Fruit & Vegetables", saved?.primaryCategory)
        assertNull(saved?.secondaryCategory)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editCategoriesInTheDropdownHandsBackTheFormAsTypedSoFar() = runComposeUiTest {
        // Arrange
        var draft: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(item = existingItem, categories = categories, onEditCategories = { draft = it })
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("Extra ")
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performClick()
        onNodeWithTag(PantryItemEditTestTags.EditCategoriesMenuItem).performClick()

        // Assert
        assertEquals(existingItem.copy(title = "Extra Heinz Ketchup"), draft)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun stockCountShowsTheItemsCurrentCount() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = existingItem) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.StockCountField).performScrollTo().assertTextEquals("4")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingPlusAndMinusAdjustsTheCountAndSavesIt() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme { PantryItemEditScreen(item = existingItem, onSave = { saved = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.StockIncreaseButton).performScrollTo().performClick().performClick()
        onNodeWithTag(PantryItemEditTestTags.StockDecreaseButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        onNodeWithTag(PantryItemEditTestTags.StockCountField).assertTextEquals("5")
        assertEquals(5, saved?.stockCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun minusIsDisabledAtZeroSoTheCountCannotGoNegative() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = existingItem.copy(stockCount = 1)) } }

        // Act
        onNodeWithTag(PantryItemEditTestTags.StockDecreaseButton).performScrollTo().performClick()

        // Assert
        onNodeWithTag(PantryItemEditTestTags.StockCountField).assertTextEquals("0")
        onNodeWithTag(PantryItemEditTestTags.StockDecreaseButton).assertIsNotEnabled()
    }
}
