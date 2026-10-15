package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.ui.screens.MealSlot

/**
 * Opens each fixture through its `open*DocumentSource` (or `*Sources`) from a database that already holds the fixture's
 * bytes, the way an app update meets an old install, and asserts what the source publishes and that the stored bytes are
 * not changed by opening, or damaged by the first save that follows.
 *
 * The Home roster, Home registry and Profile registry have no source of their own (their stores live in the device data
 * sync code), so they are covered by [AutomergeFixtureLoadTest] only.
 */
class AutomergeFixtureSourceTest {

    private val homeId = "home-1"

    private suspend fun database(): VestaDatabase = openInMemoryDatabase().also { it.homeQueries.insert(homeId, "Home", "family", 0L) }

    private suspend fun VestaDatabase.store(documentId: String, feature: String, fixture: String): ByteArray {
        val bytes = AutomergeFixtures.bytes(fixture)
        automergeDocumentQueries.upsert(documentId, homeId, feature, bytes, 0L)
        return bytes
    }

    private suspend fun VestaDatabase.stored(documentId: String): ByteArray =
        automergeDocumentQueries.selectById(documentId).awaitAsOne().bytes

    private fun heads(bytes: ByteArray) = Document.load(bytes).heads.map { it.toString() }

    // ---- Pantry ----------------------------------------------------------------------------------------------------

    @Test
    fun openingTheCurrentPantryPublishesItsItemsAndLeavesTheStoredBytesAlone() = runBlocking {
        // Arrange
        val database = database()
        val original = database.store(pantryDocumentId(homeId), "pantry", "pantry-current.automerge")

        // Act
        val source = openPantryDocumentSource(database, homeId)
        val items = source.contents.first().items
        val categories = source.categories.first()

        // Assert
        assertEquals(setOf("whole milk", "5000169000000", "bin bags", "runner bean seeds"), items.map { it.key }.toSet())
        assertEquals(listOf("aldi", "tesco"), items.first { it.key == "5000169000000" }.merchantKeys)
        assertEquals(22, categories.size)
        assertContentEquals(original, database.stored(pantryDocumentId(homeId)))
    }

    @Test
    fun openingAPantryWithUnknownKeysPublishesTheSameItemsAndKeepsTheUnknownKeysWhenSaved() = runBlocking {
        // Arrange
        val database = database()
        val original = database.store(pantryDocumentId(homeId), "pantry", "pantry-extra-keys.automerge")

        // Act
        val source = openPantryDocumentSource(database, homeId)
        val items = source.contents.first().items
        val untouched = database.stored(pantryDocumentId(homeId))
        source.refresh()
        val saved = Document.load(database.stored(pantryDocumentId(homeId)))

        // Assert
        assertEquals(4, items.size)
        assertContentEquals(original, untouched)
        assertEquals(AutomergeFixtures.load("pantry-extra-keys.automerge").readPantry(), saved.readPantry())
        assertTrue(saved.get(org.automerge.ObjectId.ROOT, "futureSetting").isPresent)
        assertTrue(saved.get(org.automerge.ObjectId.ROOT, "schemaVersion").isPresent)
    }

    @Test
    fun openingTheFirstShippedPantryPublishesItsItemsAndTheDefaultCategoriesWithoutWriting() = runBlocking {
        // Arrange
        val database = database()
        val original = database.store(pantryDocumentId(homeId), "pantry", "pantry-v1-no-registry.automerge")

        // Act
        val source = openPantryDocumentSource(database, homeId)
        val items = source.contents.first().items
        val categories = source.categories.first()
        source.refresh()
        val saved = Document.load(database.stored(pantryDocumentId(homeId)))

        // Assert
        assertEquals(3, items.size)
        assertEquals(DefaultPantryCategories.size, categories.size)
        assertEquals(heads(original), saved.heads.map { it.toString() }, "a Pantry with no registry is not seeded on open")
        assertFalse(saved.get(org.automerge.ObjectId.ROOT, "categories").isPresent)
    }

    // CURRENT BEHAVIOUR THAT ADR-034 INTENDS TO CHANGE (WP3): opening an older Pantry re-seeds its registry in memory
    // (openPantryDocumentSource calls migratePantryCategories), so the source already publishes the defaults and the
    // custom category is gone, though the stored bytes are only rewritten by the next save. ADR-034 says a migration must
    // never run on load and must not replace a shared map.
    @Test
    fun openingAnUnversionedPantryRegistryReseedsItInMemoryAndTheNextSavePersistsTheLoss() = runBlocking {
        // Arrange
        val database = database()
        val original = database.store(pantryDocumentId(homeId), "pantry", "pantry-categories-unversioned.automerge")

        // Act
        val source = openPantryDocumentSource(database, homeId)
        val categoriesOnOpen = source.categories.first()
        val itemsOnOpen = source.contents.first().items
        val storedAfterOpen = database.stored(pantryDocumentId(homeId))
        source.refresh()
        val storedAfterSave = database.stored(pantryDocumentId(homeId))

        // Assert
        assertEquals(DefaultPantryCategories.size, categoriesOnOpen.size, "current behaviour: the old registry is replaced on open")
        assertFalse(categoriesOnOpen.any { it.name == "Pet Supplies" }, "current behaviour: the custom category is dropped")
        assertEquals(setOf("whole milk", "dog biscuits"), itemsOnOpen.map { it.key }.toSet())
        assertContentEquals(original, storedAfterOpen, "opening itself does not persist")
        assertNotEquals(heads(original), heads(storedAfterSave), "current behaviour: the first save writes the re-seed")
        assertFalse(Document.load(storedAfterSave).readPantryCategories().any { it.name == "Pet Supplies" })
    }

    @Test
    fun openingAPantryRegistryAtMarkerOneReseedsItInMemoryAndTheNextSavePersistsTheLoss() = runBlocking {
        // Arrange
        val database = database()
        val original = database.store(pantryDocumentId(homeId), "pantry", "pantry-categories-marker-1.automerge")

        // Act
        val source = openPantryDocumentSource(database, homeId)
        val categoriesOnOpen = source.categories.first()
        val storedAfterOpen = database.stored(pantryDocumentId(homeId))
        source.refresh()
        val storedAfterSave = database.stored(pantryDocumentId(homeId))

        // Assert
        assertFalse(categoriesOnOpen.any { it.name == "Allotment" }, "current behaviour: the custom category is dropped")
        assertContentEquals(original, storedAfterOpen)
        assertNotEquals(heads(original), heads(storedAfterSave))
        assertEquals(listOf("whole milk"), Document.load(storedAfterSave).readPantry().map { it.id })
    }

    // ---- Shopping list ---------------------------------------------------------------------------------------------

    @Test
    fun openingTheCurrentShoppingListPublishesItsItemsAndLeavesTheStoredBytesAlone() = runBlocking {
        // Arrange
        val database = database()
        val pantry = openPantryDocumentSource(database, homeId)
        val original = database.store(shoppingListDocumentId(homeId), "shopping-list", "shopping-list-current.automerge")

        // Act
        val source = openShoppingListDocumentSource(database, homeId, pantry)
        val items = source.items.first()
        source.refresh()
        val saved = Document.load(database.stored(shoppingListDocumentId(homeId)))

        // Assert
        assertEquals(4, items.size)
        assertEquals(listOf("offer", "bbq"), items.first { it.key == "5000169000000" }.tags)
        assertTrue(items.first { it.key == "bin bags" }.outOfStock)
        assertEquals(heads(original), saved.heads.map { it.toString() })
        assertEquals(AutomergeFixtures.load("shopping-list-current.automerge").readShoppingList(), saved.readShoppingList())
    }

    @Test
    fun openingTheFirstShippedShoppingListPublishesItsItemsAndSavesThemBackUnchanged() = runBlocking {
        // Arrange
        val database = database()
        val pantry = openPantryDocumentSource(database, homeId)
        val original = database.store(shoppingListDocumentId(homeId), "shopping-list", "shopping-list-v1-no-tags.automerge")

        // Act
        val source = openShoppingListDocumentSource(database, homeId, pantry)
        val items = source.items.first()
        source.refresh()
        val saved = database.stored(shoppingListDocumentId(homeId))

        // Assert
        assertEquals(3, items.size)
        assertTrue(items.all { it.tags.isEmpty() && it.got == 0 && !it.outOfStock })
        assertEquals(AutomergeFixtures.load("shopping-list-v1-no-tags.automerge").readShoppingList(), Document.load(saved).readShoppingList())
        assertEquals(heads(original), heads(saved))
    }

    // ---- Merchants -------------------------------------------------------------------------------------------------

    @Test
    fun openingMerchantsWithAndWithoutUnknownKeysPublishesEveryKnownMerchantAndLeavesTheStoredBytesAlone() = runBlocking {
        listOf("merchants-current.automerge" to 3, "merchants-extra-keys.automerge" to 4).forEach { (fixture, expected) ->
            // Arrange
            val database = database()
            val original = database.store(merchantsDocumentId(homeId), "merchants", fixture)

            // Act
            val source = openMerchantsDocumentSource(database, homeId)
            val merchants = source.merchants.first()
            val untouched = database.stored(merchantsDocumentId(homeId))
            source.refresh()

            // Assert
            assertEquals(expected, merchants.size, fixture)
            assertTrue(merchants.any { it.key == "aldi" && it.latitude != null }, fixture)
            assertContentEquals(original, untouched, fixture)
            assertEquals(heads(original), heads(database.stored(merchantsDocumentId(homeId))), fixture)
        }
    }

    // ---- Cookbook --------------------------------------------------------------------------------------------------

    @Test
    fun openingTheCurrentAndTheOldCookbookPublishesEveryRecipeAndLeavesTheStoredBytesAlone() = runBlocking {
        listOf("cookbook-current.automerge" to 3, "cookbook-extra-keys.automerge" to 3, "cookbook-v1-staged-steps.automerge" to 1).forEach { (fixture, expected) ->
            // Arrange
            val database = database()
            val original = database.store(cookbookDocumentId(homeId), "cookbook", fixture)

            // Act
            val source = openCookbookDocumentSource(database, homeId)
            val recipes = source.recipes.first()
            val untouched = database.stored(cookbookDocumentId(homeId))
            source.refresh()

            // Assert
            assertEquals(expected, recipes.size, fixture)
            assertContentEquals(original, untouched, fixture)
            assertEquals(AutomergeFixtures.load(fixture).readCookbook(), Document.load(database.stored(cookbookDocumentId(homeId))).readCookbook(), fixture)
        }
    }

    // ---- Meal plan -------------------------------------------------------------------------------------------------

    @Test
    fun openingTheCurrentAndTheFirstShippedMealPlanPublishesEveryMealAndLeavesTheStoredBytesAlone() = runBlocking {
        listOf("mealplan-current.automerge", "mealplan-extra-keys.automerge", "mealplan-v1-no-eaters.automerge").forEach { fixture ->
            // Arrange
            val database = database()
            val original = database.store(mealPlanDocumentId(homeId), "mealplan", fixture)

            // Act
            val source = openMealPlanDocumentSource(database, homeId)
            val plan = source.plan.first()
            val untouched = database.stored(mealPlanDocumentId(homeId))
            source.refresh()

            // Assert
            assertEquals(listOf("bolognese", "salad"), plan.recipes(CalendarDate(2026, 10, 12), MealSlot.Dinner), fixture)
            assertContentEquals(original, untouched, fixture)
            assertEquals(AutomergeFixtures.load(fixture).readMealPlan(), Document.load(database.stored(mealPlanDocumentId(homeId))).readMealPlan(), fixture)
        }
    }

    // ---- Calendar --------------------------------------------------------------------------------------------------

    @Test
    fun openingTheHomeCalendarPublishesEveryEventAndLeavesTheStoredBytesAlone() = runBlocking {
        listOf("calendar-current.automerge", "calendar-extra-keys.automerge").forEach { fixture ->
            // Arrange
            val database = database()
            val original = database.store(calendarDocumentId(homeId), "calendar", fixture)

            // Act
            val source = CalendarSources(database).forHome(homeId)!!
            val events = source.events.first()
            val untouched = database.stored(calendarDocumentId(homeId))
            source.refresh()

            // Assert
            assertEquals(setOf("e1", "e2", "e3"), events.map { it.id }.toSet(), fixture)
            assertTrue(events.all { it.calendar == EventCalendar.Home }, fixture)
            assertContentEquals(original, untouched, fixture)
            assertEquals(heads(original), heads(database.stored(calendarDocumentId(homeId))), fixture)
        }
    }

    // ---- Tasks -----------------------------------------------------------------------------------------------------

    @Test
    fun openingTheHomeTasksPublishesEveryTaskAndDoneRecordAndLeavesTheStoredBytesAlone() = runBlocking {
        listOf("tasks-current.automerge", "tasks-extra-keys.automerge").forEach { fixture ->
            // Arrange
            val database = database()
            val original = database.store(tasksDocumentId(homeId), "tasks", fixture)

            // Act
            val source = TaskSources(database).forHome(homeId)!!
            val snapshot = source.snapshot.first()
            val untouched = database.stored(tasksDocumentId(homeId))
            source.refresh()

            // Assert
            assertEquals(setOf("t1", "t2", "t3"), snapshot.tasks.map { it.id }.toSet(), fixture)
            assertEquals(2, snapshot.done.size, fixture)
            assertTrue(snapshot.tasks.all { it.list == TaskList.Home }, fixture)
            assertContentEquals(original, untouched, fixture)
            assertEquals(heads(original), heads(database.stored(tasksDocumentId(homeId))), fixture)
        }
    }
}
