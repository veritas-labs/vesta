package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HomeNavigationBarTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noBadgeShowsWhenNobodyIsWaiting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeNavigationBar(selected = HomeTab.Home, membersBadge = 0) } }

        // Act
        // (no user interaction — checking the bar's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(HomeNavigationBarTestTags.MembersItem).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theMembersTabShowsHowManyAreWaiting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeNavigationBar(selected = HomeTab.Home, membersBadge = 3) } }

        // Act
        // (no user interaction — checking the bar's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertIsDisplayed().assertContentDescriptionEquals("3 waiting to join")
    }
}
