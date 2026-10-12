package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MarketplaceScreenTest {

    @Test
    fun localMerchantsIsTheOnlyOptionAndOpensTheMerchants() = runComposeUiTest {
        // Arrange
        var opened = 0
        setContent { VestaTheme { MarketplaceScreen(onLocalMerchantsClick = { opened++ }) } }
        onNodeWithText("Local Merchants").assertIsDisplayed()

        // Act
        onNodeWithTag(MarketplaceTestTags.LocalMerchants).performClick()

        // Assert
        assertEquals(1, opened)
    }

    @Test
    fun theBackArrowLeavesTheMarketplace() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { MarketplaceScreen(onBack = { backs++ }) } }

        // Act
        onNodeWithTag(MarketplaceTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
