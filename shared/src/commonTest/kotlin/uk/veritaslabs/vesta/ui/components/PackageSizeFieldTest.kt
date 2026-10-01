package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class PackageSizeFieldTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAmountPlusUnitAndContainerReportsTheCanonicalText() = runComposeUiTest {
        // Arrange
        var value = ""
        setContent { VestaTheme { PackageSizeField(initialValue = "", onValueChange = { value = it }) } }

        // Act
        onNodeWithTag(PackageSizeFieldTestTags.AmountField).performTextInput("15")
        onNodeWithTag(PackageSizeFieldTestTags.unit("oz")).performClick()
        onNodeWithTag(PackageSizeFieldTestTags.container("tin")).performClick()

        // Assert
        assertEquals("15 oz tin", value)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun chipsWithoutAnAmountReportBlank() = runComposeUiTest {
        // Arrange
        var value = "unchanged"
        setContent { VestaTheme { PackageSizeField(initialValue = "", onValueChange = { value = it }) } }

        // Act
        onNodeWithTag(PackageSizeFieldTestTags.container("jar")).performClick()

        // Assert
        assertEquals("", value)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anExistingValueIsParsedIntoThePickers() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PackageSizeField(initialValue = "400g can", onValueChange = {}) } }

        // Act
        // (no interaction — checking the prefilled state)

        // Assert
        onNodeWithTag(PackageSizeFieldTestTags.unit("g")).assertIsSelected()
        onNodeWithTag(PackageSizeFieldTestTags.container("tin")).assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aValueItCannotParseOpensAsFreeTextAndIsKept() = runComposeUiTest {
        // Arrange
        var value: String? = null
        setContent { VestaTheme { PackageSizeField(initialValue = "family size", onValueChange = { value = it }) } }

        // Act
        onNodeWithTag(PackageSizeFieldTestTags.CustomField).performTextReplacement("family size!")

        // Assert
        onNodeWithTag(PackageSizeFieldTestTags.CustomField).assertIsDisplayed()
        assertEquals("family size!", value)
    }
}
