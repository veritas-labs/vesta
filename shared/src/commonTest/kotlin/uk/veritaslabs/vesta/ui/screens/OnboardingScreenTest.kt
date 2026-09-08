package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingScreenTest {

    private val homes = listOf(
        HomeListItem(id = "the-johnsons", name = "The Johnsons"),
        HomeListItem(id = "flat-4b", name = "Flat 4B"),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachHomeAndTheCreateAndJoinHomeButtons() = runComposeUiTest {
        setContent { VestaTheme { OnboardingScreen(homes = homes) } }

        onNodeWithText("The Johnsons").assertIsDisplayed()
        onNodeWithText("Flat 4B").assertIsDisplayed()
        onNodeWithText("Create a home").assertIsDisplayed()
        onNodeWithText("Join a home").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAHomeInvokesOnHomeSelected() = runComposeUiTest {
        var selected: HomeListItem? = null
        setContent {
            VestaTheme { OnboardingScreen(homes = homes, onHomeSelected = { selected = it }) }
        }

        onNodeWithText("The Johnsons").performClick()

        assertEquals("the-johnsons", selected?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCreateHomeInvokesOnCreateHome() = runComposeUiTest {
        var created = false
        setContent {
            VestaTheme { OnboardingScreen(homes = homes, onCreateHome = { created = true }) }
        }

        onNodeWithText("Create a home").performClick()

        assertTrue(created)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingJoinHomeInvokesOnJoinHome() = runComposeUiTest {
        var joined = false
        setContent {
            VestaTheme { OnboardingScreen(homes = homes, onJoinHome = { joined = true }) }
        }

        onNodeWithText("Join a home").performClick()

        assertTrue(joined)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPressingAHomeOpensActionsSheetWithEditAndDelete() = runComposeUiTest {
        setContent { VestaTheme { OnboardingScreen(homes = homes) } }

        onNodeWithText("The Johnsons").performTouchInput { longClick() }

        onNodeWithText("Edit home").assertIsDisplayed()
        onNodeWithText("Delete home").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editHomeInSheetInvokesOnEditHomeForThatHome() = runComposeUiTest {
        var edited: HomeListItem? = null
        setContent {
            VestaTheme { OnboardingScreen(homes = homes, onEditHome = { edited = it }) }
        }

        onNodeWithText("Flat 4B").performTouchInput { longClick() }
        onNodeWithText("Edit home").performClick()

        assertEquals("flat-4b", edited?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteHomeInSheetInvokesOnDeleteHomeForThatHome() = runComposeUiTest {
        var deleted: HomeListItem? = null
        setContent {
            VestaTheme { OnboardingScreen(homes = homes, onDeleteHome = { deleted = it }) }
        }

        onNodeWithText("Flat 4B").performTouchInput { longClick() }
        onNodeWithText("Delete home").performClick()

        assertEquals("flat-4b", deleted?.id)
    }
}
