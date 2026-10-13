package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerEatersScreenTest {

    private val monday = CalendarDate(2026, 10, 12)
    private val people = listOf(
        MealPerson("m:mark", "Mark", isMember = true),
        MealPerson("m:sam", "Sam", isMember = true),
        MealPerson("p:ella", "Ella", isMember = false),
    )

    @Test
    fun saysWhichMealIsBeingEdited() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = emptySet()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Dinner · Mon 12").assertIsDisplayed()
    }

    @Test
    fun everyoneIsListedWithTheEatersTicked() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = setOf("m:mark", "p:ella")) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:mark")).assertIsOn()
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).assertIsOff()
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("p:ella")).assertIsOn()
    }

    @Test
    fun tickingAPersonReportsTheNewSetAndUntickingRemovesThem() = runComposeUiTest {
        // Arrange
        val reported = mutableListOf<Set<String>>()
        setContent {
            VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = setOf("m:mark"), onSetEaters = { reported += it }) }
        }

        // Act
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).performClick()
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:mark")).performClick()

        // Assert
        assertEquals(listOf(setOf("m:mark", "m:sam"), emptySet()), reported)
    }

    @Test
    fun withNoGuestsItExplainsWhoToAdd() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people.filter { it.isMember }, eating = emptySet()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerEatersTestTags.GuestsHint).assertIsDisplayed()
    }

    @Test
    fun addingAPersonReportsTheNameAndClearsTheField() = runComposeUiTest {
        // Arrange
        var added: String? = null
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = emptySet(), onAddGuest = { added = it }) } }
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).assertIsNotEnabled()

        // Act
        onNodeWithTag(MealPlannerEatersTestTags.AddField).performTextInput("Noah")
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).assertIsEnabled()
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).performClick()

        // Assert
        assertEquals("Noah", added)
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).assertIsNotEnabled()
    }

    @Test
    fun aGuestIsRenamedByTappingTheirName() = runComposeUiTest {
        // Arrange
        var renamed: Pair<String, String>? = null
        setContent {
            VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = emptySet(), onRenameGuest = { id, name -> renamed = id to name }) }
        }

        // Act
        onNodeWithTag(MealPlannerEatersTestTags.rename("p:ella")).performClick()
        onNodeWithTag(MealPlannerEatersTestTags.RenameField).performTextReplacement("Ellie")
        onNodeWithTag(MealPlannerEatersTestTags.RenameSave).performClick()

        // Assert
        assertEquals("ella" to "Ellie", renamed)
        onNodeWithTag(MealPlannerEatersTestTags.RenameField).assertDoesNotExist()
    }

    @Test
    fun aGuestCanBeRemovedButAMemberCannot() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = emptySet(), onRemoveGuest = { removed = it }) } }

        // Act
        onNodeWithTag(MealPlannerEatersTestTags.remove("p:ella")).performClick()

        // Assert
        assertEquals("ella", removed)
        onNodeWithTag(MealPlannerEatersTestTags.remove("m:mark")).assertDoesNotExist()
        onNodeWithTag(MealPlannerEatersTestTags.rename("m:mark")).assertDoesNotExist()
    }

    @Test
    fun backReportsATap() = runComposeUiTest {
        // Arrange
        var backed = false
        setContent { VestaTheme { MealPlannerEatersScreen(monday, MealSlot.Dinner, people, eating = emptySet(), onBack = { backed = true }) } }

        // Act
        onNodeWithTag(MealPlannerEatersTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
    }
}
