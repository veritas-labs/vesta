package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.components.PackageSizeFieldTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class PantryItemEditPackageScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun choosingAnAmountUnitAndContainerThenDoneReturnsTheCanonicalSize() = runComposeUiTest {
        // Arrange
        var done: String? = null
        setContent { VestaTheme { PantryItemEditPackageScreen(itemName = "Tomato Sauce", onDone = { done = it }) } }

        // Act
        onNodeWithTag(PackageSizeFieldTestTags.AmountField).performTextInput("15")
        onNodeWithTag(PackageSizeFieldTestTags.unit("oz")).performClick()
        onNodeWithTag(PackageSizeFieldTestTags.container("tin")).performClick()
        onNodeWithTag(PantryItemEditPackageTestTags.DoneButton).performClick()

        // Assert
        assertEquals("15 oz tin", done)
        onNodeWithTag(PantryItemEditPackageTestTags.Summary).assertTextEquals("15 oz tin")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doneIsDisabledForAPackagedItemUntilASizeIsChosen() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditPackageScreen() } }

        // Act
        // (no interaction — checking the initial state of a new item)

        // Assert
        onNodeWithTag(PantryItemEditPackageTestTags.PackagedChip).assertIsSelected()
        onNodeWithTag(PantryItemEditPackageTestTags.DoneButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun choosingLooseHidesThePickersAndDoneReturnsBlank() = runComposeUiTest {
        // Arrange
        var done: String? = null
        setContent { VestaTheme { PantryItemEditPackageScreen(initialValue = "6 pack", onDone = { done = it }) } }

        // Act
        onNodeWithTag(PantryItemEditPackageTestTags.LooseChip).performClick()
        val pickersShown = onAllNodesWithTag(PackageSizeFieldTestTags.AmountField).fetchSemanticsNodes().isNotEmpty()
        onNodeWithTag(PantryItemEditPackageTestTags.DoneButton).assertIsEnabled().performClick()

        // Assert
        onNodeWithTag(PantryItemEditPackageTestTags.Summary).assertTextEquals("Loose (no packaging)")
        assertEquals(false, pickersShown)
        assertEquals("", done)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anExistingBlankSizeStartsAsLooseWhenEditingAnExistingItem() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditPackageScreen(initialValue = "", startLoose = true) } }

        // Act
        // (no interaction — checking the initial state)

        // Assert
        onNodeWithTag(PantryItemEditPackageTestTags.LooseChip).assertIsSelected()
        onNodeWithTag(PantryItemEditPackageTestTags.DoneButton).assertIsEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backInvokesOnBackWithoutReturningAValue() = runComposeUiTest {
        // Arrange
        var backed = false
        var done: String? = null
        setContent {
            VestaTheme { PantryItemEditPackageScreen(initialValue = "6 pack", onDone = { done = it }, onBack = { backed = true }) }
        }

        // Act
        onNodeWithTag(PantryItemEditPackageTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
        assertEquals(null, done)
    }
}
