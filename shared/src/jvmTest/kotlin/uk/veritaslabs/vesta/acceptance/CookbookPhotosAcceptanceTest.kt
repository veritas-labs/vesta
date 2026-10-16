package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.Image
import uk.veritaslabs.vesta.automerge.CookbookBlobReferences
import uk.veritaslabs.vesta.automerge.homeIdsOf
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.blobs.BlobTransferService
import uk.veritaslabs.vesta.blobs.DeviceBlobRuntime
import uk.veritaslabs.vesta.ui.components.LocalBlobs
import uk.veritaslabs.vesta.blobs.PhotoSource
import uk.veritaslabs.vesta.blobs.SkiaPhotoEncoder
import uk.veritaslabs.vesta.blobs.testJpeg
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.vesta.ui.components.BlobImageTestTags
import uk.veritaslabs.vesta.ui.components.LocalPhotoPickerFactory
import uk.veritaslabs.vesta.ui.components.PhotoPicker
import uk.veritaslabs.vesta.ui.flows.CookbookFlow
import uk.veritaslabs.vesta.ui.screens.CookbookTestTags
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.RecipeDetailTestTags
import uk.veritaslabs.vesta.ui.screens.RecipeEditTestTags
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.kits.x0x.X0xNetworkPolicy

/**
 * Acceptance tests for photos in the Cookbook (docs/adr/ADR-023-blob-storage-and-transfer.md; see docs/testing/acceptance-tests.md). Each test is one
 * scenario, driven through the recipe screens over the real photo encoder, blob store, transfer service and cookbook documents, with the x0x mesh and
 * the photo picker faked. The cookbook document's own sync between devices is not under test here (it has its own tests): a device that "has the
 * recipe" is given its copy directly. The ADR's criteria that are not about screens are covered by `BlobTransferServiceTest` (5, 6, 9, 11) and
 * `BlobIngestTest`:
 *
 *  1. I can add a photo to a recipe from the device; the app keeps it as WebP no larger than 1600 px a side, with no location metadata.
 *  2. The photo appears on another device that is online at the same time, with no manual step.
 *  3. A device that was offline when the photo was added gets it from any device that holds it, not only the author.
 *  4. Before the photo arrives, the recipe shows a placeholder, and it says it is waiting.
 *  7. With "use mobile data" off, the recipe says its photos are waiting for Wi-Fi.
 *  8. The same photo in two recipes is stored once.
 * 10. A recipe whose photo has not arrived can still be opened, edited and saved.
 * 12. In the recipe list, a recipe with a photo shows its first photo on the left of its row, and one without shows none.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CookbookPhotosAcceptanceTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        jobs.forEach { it.cancel() }
        X0xNetworkPolicy.useMobileData.value = true
    }

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()
    private val members = mutableSetOf("a", "b", "c")
    private val scope = CoroutineScope(Dispatchers.Default)
    private val jobs = mutableListOf<Job>()

    /** A device: its own database, cookbook, blob store and transfer service over the fake mesh. */
    private inner class Device(val id: String) {
        val node = network.node(id, directStream = true)
        val database = openInMemoryDatabase()
        val cookbooks = CookbookSources(database)
        val directory = Files.createTempDirectory("photos-$id").toFile().also { it.deleteOnExit() }
        val runtime = DeviceBlobRuntime(
            database, node, CookbookBlobReferences(cookbooks), homeIdsOf(database),
            peersFor = { members - id },
            directory = directory,
            encoder = SkiaPhotoEncoder(),
            interval = 100.milliseconds,
            askBackoff = BlobTransferService.Backoff(200.milliseconds, 800.milliseconds),
        )

        init {
            runBlocking { database.homeQueries.insert(homeId, "Home", "family", 1_000L) }
        }

        fun start() {
            jobs += scope.launch { runtime.run() }
        }

        suspend fun source() = cookbooks.forHome(homeId)!!

        /** Photo files in this device's store (the `tmp` folder excluded). */
        fun storedPhotos() = directory.walkTopDown().filter { it.isFile && it.parentFile.name != "tmp" }.toList()

        /** This device's copy of the recipes of [from], as the cookbook sync would leave it. */
        suspend fun receiveRecipes(from: Device) {
            from.source().recipes.first().forEach { source().save(it) }
        }
    }

    private fun photoBytes(secret: String? = null) = testJpeg(3200, 2400, orientation = 1, secret = secret) { x, y -> (x / 11 % 256 shl 16) or (y / 7 % 256 shl 8) or 120 }

    private fun ComposeUiTest.show(device: Device, picked: ByteArray? = null) {
        val choosing: ((PhotoSource) -> Unit) -> PhotoPicker = { onPicked -> PhotoPicker { picked?.let { onPicked(PhotoSource.Bytes(it)) } } }
        setContent {
            val viewModel = androidx.compose.runtime.remember { CookbookViewModel(runBlocking { device.source() }, flowOf(emptyList())) }
            CompositionLocalProvider(LocalBlobs provides device.runtime.blobs, LocalPhotoPickerFactory provides choosing) {
                VestaTheme { CookbookFlow(viewModel, onBack = {}) }
            }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String, seconds: Int = 10) =
        waitUntil(timeoutMillis = seconds * 1_000L) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** Adds a recipe called [title] with a photo through the form, as a person would. */
    private fun ComposeUiTest.addRecipeWithPhoto(title: String) {
        waitForTag(CookbookTestTags.AddRecipeButton) // the cookbook is blank until it has loaded
        onNodeWithTag(CookbookTestTags.AddRecipeButton).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput(title)
        onNodeWithTag(RecipeEditTestTags.AddPhotoButton).performScrollTo().performClick()
        waitForTag(RecipeEditTestTags.RemovePhotoButton)
        onNodeWithTag(RecipeEditTestTags.SaveButton).performScrollTo().performClick()
        waitForText(title)
    }

    private fun ComposeUiTest.openRecipe(title: String) {
        waitForText(title)
        onNodeWithText(title).performClick()
        waitForTag(RecipeDetailTestTags.media(0))
    }

    private suspend fun recipeWithPhoto(device: Device): Recipe = device.source().recipes.first().single { it.media.isNotEmpty() }

    private suspend fun settle() = delay(300)

    @Test
    fun aPhotoIAddIsKeptSmallAsWebPWithoutMyLocation() = runComposeUiTest {
        // Arrange — Given a 3200 x 2400 camera photo carrying a GPS position, and a cookbook
        val a = Device("a").also { it.start() }
        val secret = "GPSLatitude=51.5007N-GPSLongitude=0.1246W"
        val original = photoBytes(secret)
        check(original.decodeToString().contains(secret))
        show(a, picked = original)
        waitForText("No recipes yet")

        // Act — When I add a recipe with that photo
        addRecipeWithPhoto("Soup")
        openRecipe("Soup")

        // Assert — Then the recipe shows the photo, and the device holds one WebP of at most 1600 px with no position in it
        waitForTag(BlobImageTestTags.Photo)
        val stored = a.storedPhotos().single().readBytes()
        val image = Image.makeFromEncoded(stored)
        assertEquals("WEBP", stored.decodeToString(8, 12))
        assertEquals(1600 to 1200, image.width to image.height)
        assertFalse(stored.decodeToString().contains("GPSLatitude"))
        assertTrue(stored.size < original.size)
    }

    @Test
    fun aPhotoAppearsOnAnotherDeviceThatIsOnlineAtTheSameTime() = runComposeUiTest {
        // Arrange — Given a second device online with the first, and the first adding a recipe with a photo
        val a = Device("a").also { it.start() }
        val b = Device("b").also { it.start() }
        settle()
        network.comeOnline("b")
        show(a, picked = photoBytes())
        addRecipeWithPhoto("Soup")

        // Act — When the second device has the recipe and I open it there
        runBlocking { b.receiveRecipes(a) }
        network.comeOnline("a") // b, now wanting the photo, sees a there
        show(b)
        openRecipe("Soup")

        // Assert — Then the photo shows without my doing anything
        waitForTag(BlobImageTestTags.Photo)
        onNodeWithTag(BlobImageTestTags.Photo).assertIsDisplayed()
    }

    @Test
    fun aDeviceThatWasOfflineGetsThePhotoFromAnyDeviceThatHoldsItNotJustTheAuthor() = runComposeUiTest {
        // Arrange — Given a photo added on A, then passed to C, and A gone away while B was offline
        val a = Device("a").also { it.start() }
        val c = Device("c").also { it.start() }
        settle()
        network.comeOnline("c")
        show(a, picked = photoBytes())
        addRecipeWithPhoto("Soup")
        runBlocking {
            c.receiveRecipes(a)
            network.comeOnline("a") // c, now wanting the photo, sees a there
            val hash = recipeWithPhoto(a).media.single().blob!!.hash
            val deadline = System.currentTimeMillis() + 10_000
            while (c.storedPhotos().isEmpty() && System.currentTimeMillis() < deadline) delay(50)
            assertTrue(c.storedPhotos().any { it.name == hash }, "C should hold the photo before the test goes on")
        }
        network.offline += "a"
        val b = Device("b").also { it.start() }
        runBlocking { b.receiveRecipes(a) }
        settle()

        // Act — When B sees C come online and I open the recipe on it
        network.comeOnline("c")
        show(b)
        openRecipe("Soup")

        // Assert — Then the photo shows, though the author is away
        waitForTag(BlobImageTestTags.Photo)
        assertTrue(network.transfers.values.any { it.to == "b" && it.from == "c" })
        assertFalse(network.transfers.values.any { it.to == "b" && it.from == "a" })
    }

    @Test
    fun beforeThePhotoArrivesTheRecipeShowsAPlaceholderAndSaysItIsWaiting() = runComposeUiTest {
        // Arrange — Given a recipe with a photo, on a device no holder of the photo is online with
        val a = Device("a").also { it.start() }
        val b = Device("b").also { it.start() }
        show(a, picked = photoBytes())
        addRecipeWithPhoto("Soup")
        runBlocking { b.receiveRecipes(a) }
        settle()

        // Act — When I open the recipe there
        show(b)
        openRecipe("Soup")

        // Assert — Then I see the placeholder with a "waiting for photo" note, and no error
        waitForTag(BlobImageTestTags.Waiting)
        onNodeWithTag(BlobImageTestTags.Waiting).assertIsDisplayed()
        assertTrue(onAllNodesWithTag(BlobImageTestTags.Photo).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun withMobileDataOffTheRecipeSaysItsPhotoIsWaitingForWifi() = runComposeUiTest {
        // Arrange — Given mobile data switched off, and a recipe whose photo this device lacks (and x0x, stopped for it, not answering)
        val a = Device("a").also { it.start() }
        val b = Device("b").also { it.start() }
        show(a, picked = photoBytes())
        addRecipeWithPhoto("Soup")
        runBlocking { b.receiveRecipes(a) }
        X0xNetworkPolicy.useMobileData.value = false

        // Act — When I open the recipe
        show(b)
        openRecipe("Soup")

        // Assert — Then it says the photo is waiting for Wi-Fi
        waitForTag(BlobImageTestTags.WaitingForWifi)
        onNodeWithTag(BlobImageTestTags.WaitingForWifi).assertIsDisplayed()
    }

    @Test
    fun thePhotoIStoredForTwoRecipesIsKeptOnce() = runComposeUiTest {
        // Arrange — Given a cookbook and the same photo picked each time
        val a = Device("a").also { it.start() }
        show(a, picked = photoBytes())
        waitForText("No recipes yet")

        // Act — When I add two recipes with that photo
        addRecipeWithPhoto("Soup")
        addRecipeWithPhoto("Stew")

        // Assert — Then both recipes refer to it and the device holds one file
        val recipes = runBlocking { a.source().recipes.first() }
        assertEquals(2, recipes.size)
        assertEquals(1, recipes.map { it.media.single().blob!!.hash }.toSet().size)
        assertEquals(1, a.storedPhotos().size)
    }

    @Test
    fun aRecipeWhosePhotoHasNotArrivedCanStillBeOpenedEditedAndSaved() = runComposeUiTest {
        // Arrange — Given a recipe whose photo this device lacks
        val a = Device("a").also { it.start() }
        val b = Device("b").also { it.start() }
        show(a, picked = photoBytes())
        addRecipeWithPhoto("Soup")
        runBlocking { b.receiveRecipes(a) }
        show(b)
        openRecipe("Soup")
        waitForTag(BlobImageTestTags.Waiting)

        // Act — When I edit the recipe's title and save
        onNodeWithTag(RecipeDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeDetailTestTags.EditMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextReplacement("Soup with croutons")
        onNodeWithTag(RecipeEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert — Then it is saved, and still refers to the photo
        waitForText("Soup with croutons")
        val saved = runBlocking { b.source().recipes.first().single() }
        assertEquals("Soup with croutons", saved.title)
        assertEquals(1, saved.media.count { it.blob != null })
    }

    @Test
    fun theRecipeListShowsTheFirstPhotoOfARecipeOnTheLeftOfItsRow() = runComposeUiTest {
        // Arrange — Given a cookbook with a recipe that has a photo and one that has none
        val a = Device("a").also { it.start() }
        runBlocking { a.source().save(Recipe(key = "plain", title = "Porridge")) }
        show(a, picked = photoBytes())
        waitForText("Porridge")

        // Act — When I add a recipe with a photo and am back at the list
        addRecipeWithPhoto("Soup")

        // Assert — Then the photo recipe's row shows a thumbnail, and the plain one does not
        val withPhoto = runBlocking { a.source().recipes.first().single { it.title == "Soup" } }
        // The row is clickable, which merges its children's semantics, so the thumbnail is looked up in the unmerged tree.
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(CookbookTestTags.thumbnail(withPhoto.key), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(CookbookTestTags.thumbnail(withPhoto.key), useUnmergedTree = true).assertIsDisplayed()
        assertTrue(onAllNodesWithTag(CookbookTestTags.thumbnail("plain"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
