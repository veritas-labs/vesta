package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.blobs.FakeBlobs
import uk.veritaslabs.vesta.blobs.LocalBlobs
import uk.veritaslabs.vesta.blobs.PhotoAddResult
import uk.veritaslabs.vesta.blobs.PhotoRejection
import uk.veritaslabs.vesta.blobs.PhotoSource
import uk.veritaslabs.vesta.blobs.testBlobReference
import uk.veritaslabs.vesta.ui.components.LocalPhotoPickerFactory
import uk.veritaslabs.vesta.ui.components.PhotoPicker
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class RecipeEditPhotosTest {

    /** A picker that "chooses" a photo the moment it is opened. */
    private val choosesAtOnce: (((PhotoSource) -> Unit) -> PhotoPicker) = { onPicked -> PhotoPicker { onPicked(PhotoSource.Bytes(byteArrayOf(1, 2, 3))) } }

    @Test
    fun withoutBlobMachineryThereIsNoAddPhotoButtonAndTheLinkFormIsAsBefore() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { RecipeEditScreen(recipe = null) } }

        // Assert
        onNodeWithTag(RecipeEditTestTags.AddPhotoButton).assertDoesNotExist()
        onNodeWithTag(RecipeEditTestTags.MediaPhotoChip).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withBlobMachineryThePhotoButtonReplacesTheLinkFormsPhotoChoice() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides FakeBlobs()) { RecipeEditScreen(recipe = null) } } }

        // Assert
        onNodeWithTag(RecipeEditTestTags.AddPhotoButton).performScrollTo().assertIsDisplayed()
        onNodeWithTag(RecipeEditTestTags.MediaPhotoChip).assertDoesNotExist()
        onNodeWithTag(RecipeEditTestTags.MediaVideoChip).assertDoesNotExist()
    }

    @Test
    fun aPickedPhotoIsAddedWithItsCaptionAndSavedAsABlobReference() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs()
        var saved: Recipe? = null
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalBlobs provides blobs, LocalPhotoPickerFactory provides choosesAtOnce) {
                    RecipeEditScreen(recipe = null, onSave = { saved = it })
                }
            }
        }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Soup")
        onNodeWithTag(RecipeEditTestTags.MediaCaptionField).performScrollTo().performTextInput("Lunch")

        // Act
        onNodeWithTag(RecipeEditTestTags.AddPhotoButton).performScrollTo().performClick()
        waitUntil { runCatching { onNodeWithTag(RecipeEditTestTags.removeMedia(0)).assertIsDisplayed() }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        val media = saved!!.media.single()
        assertEquals(1, blobs.added.size)
        assertEquals("Lunch", media.caption)
        assertEquals("", media.uri)
        assertEquals("b".repeat(64), media.blob?.hash)
    }

    @Test
    fun aRefusedPhotoShowsAMessageAndAddsNothing() = runComposeUiTest {
        // Arrange
        val blobs = FakeBlobs(addResult = PhotoAddResult.Rejected(PhotoRejection.TooLarge))
        var saved: Recipe? = null
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalBlobs provides blobs, LocalPhotoPickerFactory provides choosesAtOnce) {
                    RecipeEditScreen(recipe = null, onSave = { saved = it })
                }
            }
        }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Soup")

        // Act
        onNodeWithTag(RecipeEditTestTags.AddPhotoButton).performScrollTo().performClick()
        waitUntil { runCatching { onNodeWithTag(RecipeEditTestTags.PhotoError).assertExists() }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(emptyList(), saved!!.media)
    }

    @Test
    fun withBlobMachineryALinkCanStillBeAddedAsAVideo() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides FakeBlobs()) { RecipeEditScreen(recipe = null, onSave = { saved = it }) } } }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Soup")

        // Act
        onNodeWithTag(RecipeEditTestTags.MediaUriField).performScrollTo().performTextInput("https://example.com/soup.mp4")
        onNodeWithTag(RecipeEditTestTags.AddMediaButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        val media = saved!!.media.single()
        assertEquals(MediaKind.Video, media.kind)
        assertNull(media.blob)
    }

    @Test
    fun aRecipeKeepsItsExistingPhotosAndLinksAndEitherCanBeRemoved() = runComposeUiTest {
        // Arrange
        val recipe = Recipe(
            key = "r1",
            title = "Pie",
            media = listOf(
                RecipeMedia("", MediaKind.Photo, "Fresh", testBlobReference()),
                RecipeMedia("https://example.com/old.jpg", MediaKind.Photo, "Old link"),
            ),
        )
        var saved: Recipe? = null
        setContent { VestaTheme { CompositionLocalProvider(LocalBlobs provides FakeBlobs()) { RecipeEditScreen(recipe, onSave = { saved = it }) } } }

        // Act
        onNodeWithTag(RecipeEditTestTags.removeMedia(0)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf("https://example.com/old.jpg"), saved!!.media.map { it.uri })
    }
}
