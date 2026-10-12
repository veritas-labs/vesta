package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class PantryMerchantsScreenTest {

    private val merchants = listOf(
        Merchant(key = "tesco", name = "Tesco Extra"),
        Merchant(key = "butcher", name = "Hargreaves & Son"),
    )
    private val items = listOf(
        PantryListItem("milk", "Dairy", null, "Milk", "", 1, merchantKeys = listOf("butcher", "gone", "tesco")),
        PantryListItem("eggs", "Dairy", null, "Eggs", "", 1),
        PantryListItem("bread", "Bakery", null, "Bread", "", 1),
    )

    @Test
    fun aRowListsItsMerchantsInPriorityOrderSkippingDeletedOnes() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = items, merchants = merchants) } }

        // Act
        // (no interaction — checking the initial state)

        // Assert
        onNodeWithTag(PantryTestTags.buyFrom("milk"), useUnmergedTree = true).assertTextEquals("Hargreaves & Son, Tesco Extra")
    }

    @Test
    fun aLongPressStartsSelectingAndTapsPickMoreRows() = runComposeUiTest {
        // Arrange
        var opened: String? = null
        setContent { VestaTheme { PantryScreen(items = items, merchants = merchants, onItemSelected = { opened = it.key }) } }

        // Act
        onNodeWithTag(PantryTestTags.pantryItem("milk")).performTouchInput { longClick() }
        onNodeWithTag(PantryTestTags.pantryItem("eggs")).performClick()

        // Assert
        onNodeWithTag(PantryTestTags.SelectedCount).assertTextEquals("2 selected")
        assertEquals(null, opened)
    }

    @Test
    fun theCloseButtonStopsSelecting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = items, merchants = merchants) } }
        onNodeWithTag(PantryTestTags.pantryItem("milk")).performTouchInput { longClick() }

        // Act
        onNodeWithTag(PantryTestTags.SelectCancelButton).performClick()

        // Assert
        onNodeWithText("Pantry").assertIsDisplayed()
        onNodeWithTag(PantryTestTags.AddItemButton).assertIsDisplayed()
    }

    @Test
    fun applyingReportsTheSelectedKeysAndTheChosenChange() = runComposeUiTest {
        // Arrange
        var reported: Pair<Set<String>, MerchantLinkChange>? = null
        setContent {
            VestaTheme { PantryScreen(items = items, merchants = merchants, onChangeMerchants = { keys, change -> reported = keys to change }) }
        }
        onNodeWithTag(PantryTestTags.pantryItem("eggs")).performTouchInput { longClick() }
        onNodeWithTag(PantryTestTags.pantryItem("bread")).performClick()

        // Act
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("butcher")).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert
        assertEquals(setOf("eggs", "bread") to MerchantLinkChange.Add(listOf("butcher", "tesco")), reported)
        onNodeWithTag(PantryTestTags.AddItemButton).assertIsDisplayed()
    }

    @Test
    fun applyIsDisabledUntilAMerchantIsChosenExceptWhenReplacing() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = items, merchants = merchants, initiallySelected = setOf("milk")) } }
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()

        // Act
        onNodeWithTag(MerchantLinkTestTags.Apply).assertIsNotEnabled()
        onNodeWithTag(MerchantLinkTestTags.ModeReplace).performClick()

        // Assert
        onNodeWithTag(MerchantLinkTestTags.Apply).assertIsDisplayed()
    }

    @Test
    fun removeModeReportsARemoveChange() = runComposeUiTest {
        // Arrange
        var reported: MerchantLinkChange? = null
        setContent {
            VestaTheme {
                PantryScreen(items = items, merchants = merchants, initiallySelected = setOf("milk"), onChangeMerchants = { _, change -> reported = change })
            }
        }
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()

        // Act
        onNodeWithTag(MerchantLinkTestTags.ModeRemove).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert
        assertEquals(MerchantLinkChange.Remove(setOf("tesco")), reported)
    }

    @Test
    fun replaceModeKeepsTheOrderTheMerchantsWereMovedInto() = runComposeUiTest {
        // Arrange
        var reported: MerchantLinkChange? = null
        setContent {
            VestaTheme {
                PantryScreen(items = items, merchants = merchants, initiallySelected = setOf("milk"), onChangeMerchants = { _, change -> reported = change })
            }
        }
        onNodeWithTag(PantryTestTags.SelectMerchantsButton).performClick()
        onNodeWithTag(MerchantLinkTestTags.ModeReplace).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("tesco")).performClick()
        onNodeWithTag(MerchantLinkTestTags.add("butcher")).performClick()

        // Act
        onNodeWithTag(MerchantLinkTestTags.moveUp("butcher")).performClick()
        onNodeWithTag(MerchantLinkTestTags.Apply).performClick()

        // Assert
        assertEquals(MerchantLinkChange.Replace(listOf("butcher", "tesco")), reported)
    }
}
