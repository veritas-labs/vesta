package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class PantryItemEditMerchantsTest {

    private val merchants = listOf(
        Merchant(key = "tesco", name = "Tesco Extra"),
        Merchant(key = "butcher", name = "Hargreaves & Son"),
        Merchant(key = "stall", name = "Dave's Veg Stall"),
    )
    private val milk = PantryItemDetails(
        key = "milk", primaryCategory = "Dairy", title = "Milk", merchantKeys = listOf("tesco", "gone", "butcher"),
    )

    @Test
    fun theFormListsTheItemsMerchantsInPriorityOrderAndOffersTheOthers() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = milk, merchants = merchants) } }

        // Act
        onNodeWithTag(MerchantLinkTestTags.Section).performScrollTo()

        // Assert
        onNodeWithTag(MerchantLinkTestTags.linked("tesco")).assertExists()
        onNodeWithTag(MerchantLinkTestTags.moveUp("tesco")).assertIsNotEnabled()
        onNodeWithTag(MerchantLinkTestTags.moveDown("butcher")).assertIsNotEnabled()
        onNodeWithTag(MerchantLinkTestTags.add("stall")).assertExists()
        onAllNodesWithTag(MerchantLinkTestTags.add("tesco")).assertCountEquals(0)
    }

    @Test
    fun addingMovingAndRemovingMerchantsIsSavedInTheResultingOrderKeepingDeletedOnes() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent { VestaTheme { PantryItemEditScreen(item = milk, merchants = merchants, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(MerchantLinkTestTags.add("stall")).performScrollTo().performClick()
        onNodeWithTag(MerchantLinkTestTags.moveUp("stall")).performClick()
        onNodeWithTag(MerchantLinkTestTags.moveUp("stall")).performClick()
        onNodeWithTag(MerchantLinkTestTags.remove("tesco")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals(listOf("stall", "gone", "butcher"), saved?.merchantKeys)
    }

    @Test
    fun withoutMerchantsTheFormHasNoBuyFromSectionAndLeavesThemUntouched() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent { VestaTheme { PantryItemEditScreen(item = milk, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        onAllNodesWithTag(MerchantLinkTestTags.Section).assertCountEquals(0)
        assertEquals(milk.merchantKeys, saved?.merchantKeys)
    }

    @Test
    fun aHomeWithNoMerchantsSaysWhereToAddSome() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = milk, merchants = emptyList()) } }

        // Act
        onNodeWithTag(MerchantLinkTestTags.None).performScrollTo()

        // Assert
        onNodeWithTag(MerchantLinkTestTags.None).assertExists()
    }
}
