package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import uk.veritaslabs.vesta.blobs.FakeBlobs
import uk.veritaslabs.vesta.ui.components.LocalBlobs
import uk.veritaslabs.vesta.blobs.TinyImageBytes
import uk.veritaslabs.vesta.blobs.testBlobReference
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class BlobImageTest {

    private val reference = testBlobReference()

    @Test
    fun aPhotoThisDeviceLacksShowsItsPlaceholderAndTheWaitingCue() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs()

        // Act
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.Photo).assertDoesNotExist()
    }

    @Test
    fun aPhotoThisDeviceHoldsShowsThePhotoWithoutTheCue() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs(held = mutableMapOf(reference.hash to TinyImageBytes))

        // Act
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.Waiting).assertDoesNotExist()
    }

    @Test
    fun aPhotoThatArrivesLaterReplacesThePlaceholder() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs()
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Act
        blobs.arrive(reference.hash)
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.Waiting).assertDoesNotExist()
    }

    @Test
    fun anArrivalOfAnotherPhotoChangesNothing() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs()
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Act
        blobs.arrive("c".repeat(64))
        waitForIdle()

        // Assert
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.Photo).assertDoesNotExist()
    }

    @Test
    fun withNoBlobMachineryItShowsThePlaceholder() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { BlobImage(reference, Modifier.size(120.dp)) } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
    }

    @Test
    fun aMalformedPlaceholderStillShowsTheCue() = runComposeUiTest {
        // Arrange
        val damaged = testBlobReference(blur = "not a placeholder")

        // Act
        setContent { VestaTheme { BlobImage(damaged, Modifier.size(120.dp)) } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
    }

    @Test
    fun aPhotoWaitingBecauseMobileDataIsOffSaysItIsWaitingForWifi() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs().apply { wifi.value = true }

        // Act
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.Waiting).assertDoesNotExist()
    }

    @Test
    fun theCueChangesBackWhenWifiReturns() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs().apply { wifi.value = true }
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertIsDisplayed() }.isSuccess }

        // Act
        blobs.wifi.value = false
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
        onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertDoesNotExist()
    }

    @Test
    fun aPhotoThisDeviceHoldsNeverShowsAWifiCue() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs(held = mutableMapOf(reference.hash to TinyImageBytes)).apply { wifi.value = true }

        // Act
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides blobs) { BlobImage(reference, Modifier.size(120.dp)) } } }
        waitUntil { runCatching { onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertDoesNotExist()
    }
}
