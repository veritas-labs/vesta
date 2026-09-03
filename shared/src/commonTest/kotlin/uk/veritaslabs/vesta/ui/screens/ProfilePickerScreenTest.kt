package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfilePickerScreenTest {

    private val profiles = listOf(
        ProfileListItem(id = "tom", name = "Tom"),
        ProfileListItem(id = "arthur", name = "Arthur"),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachProfileAndTheCreateProfileButton() = runComposeUiTest {
        setContent { VestaTheme { ProfilePickerScreen(profiles = profiles) } }

        onNodeWithText("Tom").assertIsDisplayed()
        onNodeWithText("Arthur").assertIsDisplayed()
        onNodeWithText("Create profile").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAProfileInvokesOnProfileSelected() = runComposeUiTest {
        var selected: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onProfileSelected = { selected = it }) }
        }

        onNodeWithText("Tom").performClick()

        assertEquals("tom", selected?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCreateProfileInvokesOnCreateProfile() = runComposeUiTest {
        var created = false
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onCreateProfile = { created = true }) }
        }

        onNodeWithText("Create profile").performClick()

        assertTrue(created)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPressingAProfileOpensActionsSheetWithEditAndDelete() = runComposeUiTest {
        setContent { VestaTheme { ProfilePickerScreen(profiles = profiles) } }

        onNodeWithText("Tom").performTouchInput { longClick() }

        onNodeWithText("Edit profile").assertIsDisplayed()
        onNodeWithText("Delete profile").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editProfileInSheetInvokesOnEditProfileForThatProfile() = runComposeUiTest {
        var edited: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onEditProfile = { edited = it }) }
        }

        onNodeWithText("Arthur").performTouchInput { longClick() }
        onNodeWithText("Edit profile").performClick()

        assertEquals("arthur", edited?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteProfileInSheetInvokesOnDeleteProfileForThatProfile() = runComposeUiTest {
        var deleted: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onDeleteProfile = { deleted = it }) }
        }

        onNodeWithText("Arthur").performTouchInput { longClick() }
        onNodeWithText("Delete profile").performClick()

        assertEquals("arthur", deleted?.id)
    }
}
