package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
        // Arrange
        setContent { VestaTheme { ProfilePickerScreen(profiles = profiles) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        // Text assertions here because the profile names are the data under
        // test — a real regression (e.g. the wrong profile's name rendering)
        // should fail this test, unlike the tag-based assertion below.
        onNodeWithText("Tom").assertIsDisplayed()
        onNodeWithText("Arthur").assertIsDisplayed()
        onNodeWithTag(ProfilePickerTestTags.CreateProfileButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAProfileInvokesOnProfileSelected() = runComposeUiTest {
        // Arrange
        var selected: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onProfileSelected = { selected = it }) }
        }

        // Act
        onNodeWithTag(ProfilePickerTestTags.profileItem("tom")).performClick()

        // Assert
        assertEquals("tom", selected?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCreateProfileInvokesOnCreateProfile() = runComposeUiTest {
        // Arrange
        var created = false
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onCreateProfile = { created = true }) }
        }

        // Act
        onNodeWithTag(ProfilePickerTestTags.CreateProfileButton).performClick()

        // Assert
        assertTrue(created)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPressingAProfileOpensActionsSheetWithEditAndDelete() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ProfilePickerScreen(profiles = profiles) } }

        // Act
        onNodeWithTag(ProfilePickerTestTags.profileItem("tom")).performTouchInput { longClick() }

        // Assert
        onNodeWithTag(ProfilePickerTestTags.EditProfileButton).assertIsDisplayed()
        onNodeWithTag(ProfilePickerTestTags.DeleteProfileButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editProfileInSheetInvokesOnEditProfileForThatProfile() = runComposeUiTest {
        // Arrange
        var edited: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onEditProfile = { edited = it }) }
        }

        // Act
        onNodeWithTag(ProfilePickerTestTags.profileItem("arthur")).performTouchInput { longClick() }
        onNodeWithTag(ProfilePickerTestTags.EditProfileButton).performClick()

        // Assert
        assertEquals("arthur", edited?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteProfileInSheetInvokesOnDeleteProfileForThatProfile() = runComposeUiTest {
        // Arrange
        var deleted: ProfileListItem? = null
        setContent {
            VestaTheme { ProfilePickerScreen(profiles = profiles, onDeleteProfile = { deleted = it }) }
        }

        // Act
        onNodeWithTag(ProfilePickerTestTags.profileItem("arthur")).performTouchInput { longClick() }
        onNodeWithTag(ProfilePickerTestTags.DeleteProfileButton).performClick()

        // Assert
        assertEquals("arthur", deleted?.id)
    }
}
