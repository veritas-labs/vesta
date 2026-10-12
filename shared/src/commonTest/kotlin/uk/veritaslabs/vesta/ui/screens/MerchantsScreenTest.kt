package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MerchantsScreenTest {

    private val butcher = Merchant(
        key = "butcher", name = "Hargreaves", type = MerchantType.IndependentTrader,
        services = listOf(MerchantService.Butcher, MerchantService.Deli), customServices = listOf("Game"),
        hours = OpeningHours(
            mapOf(Weekday.Monday to listOf(OpenRange(9 * 60, 13 * 60), OpenRange(14 * 60, 17 * 60)), Weekday.Saturday to listOf(OpenRange(8 * 60, 13 * 60))),
            note = "Closed bank holidays",
        ),
        latitude = 53.4808, longitude = -2.2426, address = "12 Market Street", gersId = "gers-123", x0xAgentId = "agent-456",
    )
    private val tesco = Merchant(key = "tesco", name = "Tesco", type = MerchantType.NationalChain, services = listOf(MerchantService.Groceries))

    @Test
    fun showsAMessageWhenThereAreNoMerchants() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantsScreen(merchants = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("No merchants yet").assertIsDisplayed()
        onNodeWithTag(MerchantsTestTags.SearchField).assertDoesNotExist()
    }

    @Test
    fun eachRowShowsNameTypeAndServices() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantsScreen(merchants = listOf(butcher)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Hargreaves").assertIsDisplayed()
        onNodeWithTag(MerchantsTestTags.merchant("butcher")).assertTextContains("Independent trader")
        onNodeWithText("Butcher, Deli, Game").assertIsDisplayed()
    }

    @Test
    fun tappingARowReportsTheMerchantAndTheButtonAddsOne() = runComposeUiTest {
        // Arrange
        var selected: Merchant? = null
        var added = false
        setContent { VestaTheme { MerchantsScreen(listOf(butcher, tesco), onMerchantSelected = { selected = it }, onAddMerchant = { added = true }) } }

        // Act
        onNodeWithTag(MerchantsTestTags.merchant("tesco")).performClick()
        onNodeWithTag(MerchantsTestTags.AddMerchantButton).performClick()

        // Assert
        assertEquals(tesco, selected)
        assertEquals(true, added)
    }

    @Test
    fun theFilterHidesMerchantsThatDoNotMatch() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantsScreen(listOf(butcher, tesco), filter = MerchantsFilter(type = MerchantType.NationalChain)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Tesco").assertIsDisplayed()
        onNodeWithText("Hargreaves").assertDoesNotExist()
    }

    @Test
    fun theTypeChipsAreOnlyForTypesInUseAndReportTheirToggle() = runComposeUiTest {
        // Arrange
        var toggled: MerchantType? = null
        setContent { VestaTheme { MerchantsScreen(listOf(butcher, tesco), onToggleType = { toggled = it }) } }

        // Act
        onNodeWithTag(MerchantsTestTags.typeChip(MerchantType.NationalChain)).performClick()

        // Assert
        assertEquals(MerchantType.NationalChain, toggled)
        onNodeWithTag(MerchantsTestTags.typeChip(MerchantType.MarketTrader)).assertDoesNotExist()
    }

    @Test
    fun theDetailPageShowsServicesHoursLocationAndIds() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantDetailScreen(butcher) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MerchantDetailTestTags.service("Butcher")).assertIsDisplayed()
        onNodeWithTag(MerchantDetailTestTags.service("Game")).assertIsDisplayed()
        onNodeWithTag(MerchantDetailTestTags.hours(Weekday.Monday), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("09:00–13:00, 14:00–17:00").assertIsDisplayed()
        onNodeWithText("08:00–13:00").assertIsDisplayed()
        onNodeWithTag(MerchantDetailTestTags.HoursNote).assertTextEquals("Closed bank holidays")
        onNodeWithTag(MerchantDetailTestTags.Address).assertTextEquals("12 Market Street")
        onNodeWithTag(MerchantDetailTestTags.Coordinates).assertTextEquals("53.4808, -2.2426")
        onNodeWithTag(MerchantDetailTestTags.GersId).assertTextEquals("gers-123")
        onNodeWithTag(MerchantDetailTestTags.AgentId).assertTextEquals("agent-456")
    }

    @Test
    fun theDetailPageLeavesOutWhatTheMerchantDoesNotHave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantDetailScreen(tesco) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MerchantDetailTestTags.GersId).assertDoesNotExist()
        onNodeWithTag(MerchantDetailTestTags.AgentId).assertDoesNotExist()
        onNodeWithTag(MerchantDetailTestTags.Coordinates).assertDoesNotExist()
        onNodeWithTag(MerchantDetailTestTags.hours(Weekday.Monday)).assertDoesNotExist()
    }

    @Test
    fun deletingFromTheDetailPageAsksFirst() = runComposeUiTest {
        // Arrange
        var deleted = false
        setContent { VestaTheme { MerchantDetailScreen(butcher, onDelete = { deleted = true }) } }

        // Act
        onNodeWithTag(MerchantDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(MerchantDetailTestTags.DeleteMenuItem).performClick()
        val deletedBeforeConfirming = deleted
        onNodeWithTag(MerchantDetailTestTags.ConfirmDelete).performClick()

        // Assert
        assertEquals(false, deletedBeforeConfirming)
        assertEquals(true, deleted)
    }

    @Test
    fun saveIsDisabledUntilTheMerchantHasAName() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantEditScreen(merchant = null) } }
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().assertIsNotEnabled()

        // Act
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Aldi")

        // Assert
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().assertIsEnabled()
    }

    @Test
    fun savingReportsWhatWasEntered() = runComposeUiTest {
        // Arrange
        var saved: Merchant? = null
        setContent { VestaTheme { MerchantEditScreen(merchant = null, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Dave's Veg")
        onNodeWithTag(MerchantEditTestTags.typeChip(MerchantType.MarketTrader)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.serviceChip(MerchantService.Greengrocer)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.CustomServicesField).performScrollTo().performTextInput("Eggs, greengrocer, Honey")
        onNodeWithTag(MerchantEditTestTags.addRange(Weekday.Saturday)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.openField(Weekday.Saturday, 0)).performScrollTo().performTextInput("8:00")
        onNodeWithTag(MerchantEditTestTags.closeField(Weekday.Saturday, 0)).performScrollTo().performTextInput("16:00")
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextInput("53.48")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextInput("-2.24")
        onNodeWithTag(MerchantEditTestTags.GersIdField).performScrollTo().performTextInput(" gers-1 ")
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals(
            Merchant(
                name = "Dave's Veg", type = MerchantType.MarketTrader, services = listOf(MerchantService.Greengrocer), customServices = listOf("Eggs", "Honey"),
                hours = OpeningHours(mapOf(Weekday.Saturday to listOf(OpenRange(8 * 60, 16 * 60)))),
                latitude = 53.48, longitude = -2.24, gersId = "gers-1",
            ),
            saved,
        )
    }

    @Test
    fun aLoneOrOutOfRangeCoordinateShowsAnErrorAndBlocksSaving() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantEditScreen(merchant = null) } }
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Aldi")

        // Act
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextInput("95")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextInput("10")

        // Assert
        onNodeWithTag(MerchantEditTestTags.CoordinatesError).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aTimeThatIsNotATimeShowsAnErrorAndBlocksSaving() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantEditScreen(merchant = null) } }
        onNodeWithTag(MerchantEditTestTags.NameField).performTextInput("Aldi")

        // Act
        onNodeWithTag(MerchantEditTestTags.addRange(Weekday.Monday)).performScrollTo().performClick()
        onNodeWithTag(MerchantEditTestTags.openField(Weekday.Monday, 0)).performScrollTo().performTextInput("late")

        // Assert
        onNodeWithTag(MerchantEditTestTags.HoursError).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun editingStartsFromTheMerchantAndKeepsItsKeyWhenSaved() = runComposeUiTest {
        // Arrange
        var saved: Merchant? = null
        setContent { VestaTheme { MerchantEditScreen(butcher, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals(butcher, saved)
    }

    @Test
    fun clearingTheCoordinatesSavesNoLocation() = runComposeUiTest {
        // Arrange
        var saved: Merchant? = null
        setContent { VestaTheme { MerchantEditScreen(butcher, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(MerchantEditTestTags.LatitudeField).performScrollTo().performTextReplacement("")
        onNodeWithTag(MerchantEditTestTags.LongitudeField).performScrollTo().performTextReplacement("")
        onNodeWithTag(MerchantEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertNull(saved?.latitude)
        assertNull(saved?.longitude)
    }
}
