package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileAvatarTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsInitialWhenNoAvatarProvided() = runComposeUiTest {
        setContent { VestaTheme { ProfileAvatar(name = "Mei", size = 40.dp) } }

        onNodeWithText("M").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun asAvatarButtonSlotFallsBackToInitialsAndRespondsToClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                AvatarButton(
                    label = "Mei",
                    avatar = { size -> ProfileAvatar(name = "Mei", size = size) },
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("M").assertIsDisplayed()
        onNodeWithText("Mei").performClick()

        assertTrue(clicked)
    }

    @Test
    fun initialsFor_usesFirstLetterUppercased() {
        assertEquals("A", initialsFor("arthur"))
        assertEquals("T", initialsFor("  tom"))
        assertEquals("", initialsFor(""))
        assertEquals("", initialsFor("   "))
    }
}
