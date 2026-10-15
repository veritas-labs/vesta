package uk.veritaslabs.vesta.automerge

import java.util.Date
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.vesta.ui.screens.MediaKind
import uk.veritaslabs.vesta.ui.screens.MerchantService
import uk.veritaslabs.vesta.ui.screens.MerchantType
import uk.veritaslabs.vesta.ui.screens.PlannedMeal
import uk.veritaslabs.vesta.ui.screens.RecipeType
import uk.veritaslabs.vesta.ui.screens.Weekday

/**
 * Loads every checked-in fixture (see [AutomergeFixtures]) with the current code and asserts what the readers return
 * (ADR-034 "Tests"). A fixture that gains an assertion keeps it: these files are what real devices hold.
 */
class AutomergeFixtureLoadTest {

    // ---- Every fixture ---------------------------------------------------------------------------------------------

    @Test
    fun everyFixtureInTheDirectoryIsListedAndEveryListedFixtureExists() {
        // Arrange
        val onDisk = AutomergeFixtures.sourceDirectory.listFiles { file -> file.extension == "automerge" }.orEmpty().map { it.name }.toSet()

        // Act
        val listed = AutomergeFixtures.generators.keys

        // Assert
        assertEquals(listed, onDisk)
    }

    @Test
    fun everyFixtureLoadsAndSavesBackToTheSameHeadsWithoutWritingAnything() {
        AutomergeFixtures.generators.keys.forEach { name ->
            // Arrange
            val bytes = AutomergeFixtures.bytes(name)

            // Act
            val doc = Document.load(bytes)
            val headsOnLoad = doc.heads.map { it.toString() }
            val reloaded = Document.load(doc.save())

            // Assert
            assertTrue(bytes.isNotEmpty(), name)
            assertEquals(headsOnLoad, doc.heads.map { it.toString() }, "$name: reading must not change the heads")
            assertEquals(headsOnLoad, reloaded.heads.map { it.toString() }, "$name: save and load must keep the heads")
        }
    }

    // ---- Pantry ----------------------------------------------------------------------------------------------------

    @Test
    fun theFirstShippedPantryHasNoRegistryAndReadsAsTheDefaultCategories() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-v1-no-registry.automerge")

        // Act
        val items = doc.readPantry()
        val categories = doc.readPantryCategories()

        // Assert
        assertEquals(listOf("5000169000000", "whole milk", "beef mince"), items.map { it.id })
        val milk = items.first { it.id == "whole milk" }
        assertEquals(PantryItem("whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", null, 3, "2026-01-05T10:00:00Z", 2), milk)
        assertEquals(emptyList(), items.flatMap { it.merchantKeys })
        assertNull(items.first { it.id == "beef mince" }.secondaryCategory)
        assertEquals("5000169000000", items.first { it.id == "5000169000000" }.barcode)
        assertEquals(DefaultPantryCategories.map { it.name }.sortedBy(String::lowercase), categories.map { it.name })
        assertTrue(doc.isPantrySeeded())
    }

    @Test
    fun aPantryRegistryWithoutAVersionMarkerKeepsItsOldAndCustomCategoriesUntilMigrated() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-categories-unversioned.automerge")

        // Act
        val categories = doc.readPantryCategories()
        val items = doc.readPantry()

        // Assert
        assertEquals(16, categories.size)
        assertEquals(listOf("Treats"), categories.first { it.name == "Pet Supplies" }.secondary)
        assertContains(categories.first { it.name == "Dairy" }.secondary, "Milk")
        assertEquals(setOf("whole milk", "dog biscuits"), items.map { it.id }.toSet())
        assertEquals(1, items.first { it.id == "dog biscuits" }.stockCount)
    }

    @Test
    fun aPantryRegistryAtMarkerOneKeepsItsCategoriesUntilMigrated() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-categories-marker-1.automerge")

        // Act
        val categories = doc.readPantryCategories()

        // Assert
        assertEquals(listOf("Allotment", "Dairy", "Meat"), categories.map { it.name })
        assertEquals(listOf("Beans"), categories.first { it.name == "Allotment" }.secondary)
        assertEquals(1, doc.readPantry().size)
    }

    @Test
    fun theCurrentPantryReadsEveryItemWithAndWithoutMerchantKeysAndEveryCategory() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-current.automerge")

        // Act
        val items = doc.readPantry().associateBy { it.id }
        val categories = doc.readPantryCategories()

        // Assert
        assertEquals(setOf("whole milk", "5000169000000", "bin bags", "runner bean seeds"), items.keys)
        assertEquals(2, items.getValue("whole milk").useCount)
        assertEquals(2, items.getValue("whole milk").stockCount)
        assertEquals(emptyList(), items.getValue("whole milk").merchantKeys)
        assertEquals(listOf("aldi", "tesco"), items.getValue("5000169000000").merchantKeys)
        assertEquals("5000169000000", items.getValue("5000169000000").barcode)
        assertNull(items.getValue("bin bags").secondaryCategory)
        assertNull(items.getValue("bin bags").barcode)
        assertEquals(listOf("garden-centre"), items.getValue("runner bean seeds").merchantKeys)
        assertEquals(22, categories.size)
        assertContains(categories.first { it.name == "Dairy & Eggs" }.secondary, "Goat Milk")
        assertContains(categories.map { it.name }, "Allotment")
    }

    @Test
    fun aPantryWithUnknownKeysReadsTheSameItemsAndCategoriesAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("pantry-current.automerge")
        val extras = AutomergeFixtures.load("pantry-extra-keys.automerge")

        // Act
        val items = extras.readPantry()
        val categories = extras.readPantryCategories()

        // Assert
        assertEquals(4, items.size)
        assertEquals(current.readPantry(), items)
        assertEquals(current.readPantryCategories(), categories)
        assertEquals("on", (extras.get(ObjectId.ROOT, "futureSetting").getOrNull() as org.automerge.AmValue.Str).value)
    }

    @Test
    fun migratingTheCurrentPantryOrOneWithoutARegistryWritesNothing() {
        listOf("pantry-current.automerge", "pantry-extra-keys.automerge", "pantry-v1-no-registry.automerge").forEach { name ->
            // Arrange
            val doc = AutomergeFixtures.load(name)
            val before = doc.heads.map { it.toString() }

            // Act
            migratePantryCategories(doc)

            // Assert
            assertEquals(before, doc.heads.map { it.toString() }, "$name: opening must not write when there is nothing to migrate")
        }
    }

    // ADR-010 Amendment 2 / ADR-034 WP3: migratePantryCategories edits the existing `categories` map in place at
    // deterministic keys, keeps what the household added and removes only defaults the current set dropped.
    @Test
    fun migratingAnUnversionedPantryRegistryKeepsCustomCategoriesAndItems() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-categories-unversioned.automerge")
        val before = doc.heads.map { it.toString() }

        // Act
        migratePantryCategories(doc)

        // Assert
        assertNotEquals(before, doc.heads.map { it.toString() }, "an old registry is brought up to the current defaults")
        val categories = doc.readPantryCategories()
        assertTrue(categories.any { it.name == "Pet Supplies" }, "the household's own category survives")
        assertTrue(DefaultPantryCategories.all { default -> categories.any { it.name == default.name } })
        assertFalse(categories.any { it.name == "Meat" }, "a default the current set dropped is removed")
        assertEquals(setOf("whole milk", "dog biscuits"), doc.readPantry().map { it.id }.toSet())
        assertEquals(2, (doc.get(ObjectId.ROOT, "categoriesVersion").getOrNull() as org.automerge.AmValue.Int).value)
    }

    @Test
    fun migratingAPantryRegistryAtMarkerOneKeepsCustomCategoriesAndItems() {
        // Arrange
        val doc = AutomergeFixtures.load("pantry-categories-marker-1.automerge")
        val before = doc.heads.map { it.toString() }

        // Act
        migratePantryCategories(doc)

        // Assert
        assertNotEquals(before, doc.heads.map { it.toString() }, "an old registry is brought up to the current defaults")
        assertTrue(doc.readPantryCategories().any { it.name == "Allotment" }, "the household's own category survives")
        assertEquals(listOf("whole milk"), doc.readPantry().map { it.id })
    }

    @Test
    fun twoDevicesMigratingTheSameOldPantryKeepOneCategoriesMapAndALaterAddition() {
        // Arrange
        val first = AutomergeFixtures.load("pantry-categories-unversioned.automerge")
        val second = first.fork()
        migratePantryCategories(first)
        migratePantryCategories(second)
        // A custom category one device adds after migrating.
        addPrimaryCategory(second, "Custom After Migrating")

        // Act
        first.merge(second)
        val conflicts = first.getAll(ObjectId.ROOT, "categories").getOrNull()?.values().orEmpty()

        // Assert
        assertEquals(1, conflicts.size, "concurrent migrations edit the one map in place, so nothing competes")
        val names = first.readPantryCategories().map { it.name }
        assertContains(names, "Custom After Migrating")
        assertContains(names, "Pet Supplies")
    }

    // ---- Shopping list ---------------------------------------------------------------------------------------------

    @Test
    fun theFirstShippedShoppingListReadsWithNoTagsProgressOrOutOfStock() {
        // Arrange
        val doc = AutomergeFixtures.load("shopping-list-v1-no-tags.automerge")

        // Act
        val items = doc.readShoppingList()

        // Assert
        assertEquals(listOf("whole milk", "bin bags", "5000169000000"), items.map { it.id })
        assertEquals(ShoppingItem("whole milk", "Dairy", "Milk", "Whole Milk", 2, "2 pint", null), items.first { it.id == "whole milk" })
        assertTrue(items.all { it.tags.isEmpty() && it.got == 0 && !it.outOfStock })
        assertNull(items.first { it.id == "bin bags" }.secondaryCategory)
        assertTrue(doc.isShoppingListSeeded())
    }

    @Test
    fun theCurrentShoppingListReadsEveryItemWithAndWithoutTagsAndProgress() {
        // Arrange
        val doc = AutomergeFixtures.load("shopping-list-current.automerge")

        // Act
        val items = doc.readShoppingList()

        // Assert
        assertEquals(listOf("Bakery", "Dairy", "Household", "Tins & Jars"), items.map { it.primaryCategory })
        val milk = items.first { it.id == "whole milk" }
        assertEquals(1, milk.got)
        assertEquals(emptyList(), milk.tags)
        assertEquals(listOf("offer", "bbq"), items.first { it.id == "5000169000000" }.tags)
        assertEquals(4, items.first { it.id == "5000169000000" }.got)
        assertTrue(items.first { it.id == "bin bags" }.outOfStock)
        assertEquals(listOf("Saturday"), items.first { it.id == "sourdough" }.tags)
    }

    @Test
    fun aShoppingListWithUnknownKeysReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("shopping-list-current.automerge")
        val extras = AutomergeFixtures.load("shopping-list-extra-keys.automerge")

        // Act
        val items = extras.readShoppingList()

        // Assert
        assertEquals(4, items.size)
        assertEquals(current.readShoppingList(), items)
    }

    // ---- Merchants -------------------------------------------------------------------------------------------------

    @Test
    fun theCurrentMerchantsReadWithAndWithoutLocationHoursAndServices() {
        // Arrange
        val doc = AutomergeFixtures.load("merchants-current.automerge")

        // Act
        val merchants = doc.readMerchants()

        // Assert
        assertEquals(listOf("Aldi", "Dave's Veg Stall", "Online Grocer"), merchants.map { it.name })
        val aldi = merchants.first { it.key == "aldi" }
        assertEquals(MerchantType.NationalChain, aldi.type)
        assertEquals(listOf(MerchantService.Groceries, MerchantService.Bakery), aldi.services)
        assertEquals(listOf("Click & collect"), aldi.customServices)
        assertEquals(2, aldi.hours.on(Weekday.Saturday).size)
        assertEquals("Closed Sunday", aldi.hours.note)
        assertEquals(51.5074, aldi.latitude!!, 1e-9)
        assertEquals(-0.1278, aldi.longitude!!, 1e-9)
        assertEquals("ab".repeat(32), aldi.x0xAgentId)
        val stall = merchants.first { it.key == "dave-veg" }
        assertFalse(stall.hasLocation)
        assertTrue(stall.hours.isEmpty)
        assertEquals(MerchantType.MarketTrader, stall.type)
    }

    @Test
    fun merchantsWithUnknownKeysStillReadEveryKnownMerchantAndFallBackForUnknownValues() {
        // Arrange
        val current = AutomergeFixtures.load("merchants-current.automerge").readMerchants()
        val extras = AutomergeFixtures.load("merchants-extra-keys.automerge")

        // Act
        val merchants = extras.readMerchants()

        // Assert
        assertEquals(current, merchants.filter { it.key != "future-merchant" })
        val future = merchants.first { it.key == "future-merchant" }
        assertEquals("Drone Depot", future.name)
        // An unknown type reads as Other and an unknown service as a custom one: nothing is dropped, but a save from this
        // version would write those back as "Other" and a custom service (the lossy half of reading tolerantly).
        assertEquals(MerchantType.Other, future.type)
        assertEquals(listOf("TeleportBooth"), future.customServices)
    }

    // ---- Cookbook --------------------------------------------------------------------------------------------------

    @Test
    fun anOldCookbookWithStagedStepsAndLinkOnlyMediaReadsItsStepsAndLinks() {
        // Arrange
        val doc = AutomergeFixtures.load("cookbook-v1-staged-steps.automerge")

        // Act
        val recipes = doc.readCookbook()

        // Assert
        assertEquals(1, recipes.size)
        val soup = recipes.single()
        assertEquals("Tomato Soup", soup.title)
        assertEquals(RecipeType.Starter, soup.type)
        assertEquals(listOf("s1", "s2"), soup.steps.map { it.id })
        assertEquals(listOf(null, 20), soup.steps.map { it.minutes })
        assertEquals("https://example.com/soup.jpg", soup.media.single().uri)
        assertNull(soup.media.single().blob)
        assertEquals(800.0, soup.ingredients.single().quantity!!, 0.0)
        assertEquals(listOf("soup"), soup.tags)
        assertEquals(emptySet(), doc.readCookbookBlobReferences())
    }

    @Test
    fun theCurrentCookbookReadsEveryRecipeWithAndWithoutMediaQuantitiesAndMinutes() {
        // Arrange
        val doc = AutomergeFixtures.load("cookbook-current.automerge")

        // Act
        val recipes = doc.readCookbook()

        // Assert
        assertEquals(listOf("Pancakes", "Spaghetti Bolognese", "Toast"), recipes.map { it.title })
        val bolognese = recipes.first { it.key == "bolognese" }
        assertEquals(RecipeType.Dinner, bolognese.type)
        assertEquals(5, bolognese.rating)
        assertTrue(bolognese.favourite)
        assertEquals(listOf("Italian", "family"), bolognese.tags)
        assertEquals(listOf(MediaKind.Photo, MediaKind.Video), bolognese.media.map { it.kind })
        assertEquals(AutomergeFixtures.PhotoHash, bolognese.media.first().blob?.hash)
        assertNull(bolognese.media.last().blob)
        assertEquals(listOf(500.0, 400.0, null), bolognese.ingredients.map { it.quantity })
        assertEquals(listOf(10, null, 11), bolognese.steps.map { it.minutes })
        assertEquals(setOf(AutomergeFixtures.PhotoHash), doc.readCookbookBlobReferences().map { it.hash }.toSet())
        assertEquals(emptyList(), recipes.first { it.key == "toast" }.steps)
    }

    @Test
    fun aCookbookWithUnknownKeysReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("cookbook-current.automerge").readCookbook()
        val extras = AutomergeFixtures.load("cookbook-extra-keys.automerge")

        // Act
        val recipes = extras.readCookbook()

        // Assert
        assertEquals(3, recipes.size)
        assertEquals(current, recipes)
    }

    // ---- Meal plan -------------------------------------------------------------------------------------------------

    @Test
    fun theFirstShippedMealPlanReadsItsMealsWithNoGuestsOrEaters() {
        // Arrange
        val doc = AutomergeFixtures.load("mealplan-v1-no-eaters.automerge")

        // Act
        val plan = doc.readMealPlan()

        // Assert
        assertEquals(listOf("bolognese", "salad"), plan.recipes(CalendarDate(2026, 10, 12), MealSlot.Dinner))
        assertEquals(listOf("pancakes"), plan.recipes(CalendarDate(2026, 10, 13), MealSlot.Breakfast))
        assertEquals(emptyList(), plan.guests)
        assertEquals(emptyMap(), plan.eaters)
    }

    @Test
    fun theCurrentMealPlanReadsMealsGuestsAndChosenEaters() {
        // Arrange
        val doc = AutomergeFixtures.load("mealplan-current.automerge")

        // Act
        val plan = doc.readMealPlan()

        // Assert
        assertEquals(listOf("bolognese", "salad"), plan.recipes(CalendarDate(2026, 10, 12), MealSlot.Dinner))
        assertEquals(listOf("soup"), plan.recipes(CalendarDate(2026, 10, 13), MealSlot.Lunch))
        assertEquals(listOf("Ben", "Grandad"), plan.guests.map { it.name })
        assertEquals(setOf("m:mum", "p:g1"), plan.chosenEaters(CalendarDate(2026, 10, 12), MealSlot.Dinner, "bolognese"))
        assertNull(plan.chosenEaters(CalendarDate(2026, 10, 12), MealSlot.Dinner, "salad"))
        assertEquals(setOf(PlannedMeal(CalendarDate(2026, 10, 12), MealSlot.Dinner, "bolognese")), plan.eaters.keys)
    }

    @Test
    fun aMealPlanWithUnknownKeysSlotsAndDatesReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("mealplan-current.automerge").readMealPlan()
        val extras = AutomergeFixtures.load("mealplan-extra-keys.automerge")

        // Act
        val plan = extras.readMealPlan()

        // Assert
        assertEquals(current, plan)
    }

    // ---- Calendar --------------------------------------------------------------------------------------------------

    @Test
    fun theCurrentCalendarReadsTimedAllDayAndRepeatingEvents() {
        // Arrange
        val doc = AutomergeFixtures.load("calendar-current.automerge")

        // Act
        val events = doc.readCalendarEvents(EventCalendar.Home).associateBy { it.id }

        // Assert
        assertEquals(setOf("e1", "e2", "e3"), events.keys)
        assertEquals("Dentist", events.getValue("e1").title)
        assertEquals("High St", events.getValue("e1").location)
        assertEquals(2, events.getValue("e1").color)
        assertTrue(events.getValue("e2").allDay)
        assertEquals(EventRepeat.Yearly, events.getValue("e2").repeat)
        assertEquals(setOf("m:mum", "p:g1"), events.getValue("e2").participants)
        assertEquals("", events.getValue("e3").location)
        assertEquals(EventRepeat.Weekly, events.getValue("e3").repeat)
    }

    @Test
    fun aCalendarWithUnknownKeysReadsEveryKnownEventAndFallsBackForUnknownValues() {
        // Arrange
        val current = AutomergeFixtures.load("calendar-current.automerge").readCalendarEvents(EventCalendar.Home).associateBy { it.id }
        val extras = AutomergeFixtures.load("calendar-extra-keys.automerge")

        // Act
        val events = extras.readCalendarEvents(EventCalendar.Home).associateBy { it.id }

        // Assert
        assertEquals(setOf("e1", "e2", "e3"), events.keys, "the event with no start is skipped on purpose; nothing else is")
        assertEquals(current.getValue("e1"), events.getValue("e1"))
        assertEquals(current.getValue("e2"), events.getValue("e2"))
        assertEquals(EventRepeat.None, events.getValue("e3").repeat)
        assertEquals(7, events.getValue("e3").color)
    }

    // ---- Tasks -----------------------------------------------------------------------------------------------------

    @Test
    fun theCurrentTasksReadWithAndWithoutDueTimeRepeatAndAssignees() {
        // Arrange
        val doc = AutomergeFixtures.load("tasks-current.automerge")

        // Act
        val snapshot = doc.readTasks(TaskList.Home)

        // Assert
        val tasks = snapshot.tasks.associateBy { it.id }
        assertEquals(setOf("t1", "t2", "t3"), tasks.keys)
        assertEquals(CalendarDate(2026, 10, 15), tasks.getValue("t1").due)
        assertEquals(1140, tasks.getValue("t1").timeMinute)
        assertEquals(setOf("m:mum"), tasks.getValue("t1").assignees)
        assertNull(tasks.getValue("t2").due)
        assertNull(tasks.getValue("t2").timeMinute)
        assertEquals("Roof felt", tasks.getValue("t2").description)
        assertEquals(3, tasks.getValue("t3").every)
        assertEquals(setOf("t1" to "2026-10-08", "t2" to "anytime"), snapshot.done.map { it.taskId to it.occurrence }.toSet())
        assertEquals("m:dad", snapshot.done.first { it.taskId == "t2" }.by)
    }

    @Test
    fun tasksWithUnknownKeysReadTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("tasks-current.automerge").readTasks(TaskList.Home)
        val extras = AutomergeFixtures.load("tasks-extra-keys.automerge")

        // Act
        val snapshot = extras.readTasks(TaskList.Home)

        // Assert
        assertEquals(3, snapshot.tasks.size)
        assertEquals(2, snapshot.done.size)
        assertEquals(current, snapshot)
    }

    // ---- Home roster -----------------------------------------------------------------------------------------------

    @Test
    fun anOldRosterWithASingleHubReadsMembersDevicesGroupHubAndInvite() {
        // Arrange
        val doc = AutomergeFixtures.load("roster-v1-single-hub.automerge")

        // Act
        val members = doc.readRoster().associateBy { it.memberId }

        // Assert
        assertEquals(setOf("mum", "dad"), members.keys)
        assertTrue(members.values.all { it.isComplete && !it.removed })
        assertEquals(setOf("agent-mum-phone"), members.getValue("mum").devices)
        assertEquals("admin", members.getValue("mum").role)
        assertEquals("group-1", doc.readRosterGroup())
        assertEquals(setOf("agent-hub"), doc.readRosterHubs())
        assertEquals(emptyMap(), doc.readRosterDeviceNames())
        assertEquals(emptyMap(), doc.readRosterGone())
        val invite = doc.readInvites().single()
        assertEquals("hash1", invite.secretHash)
        assertTrue(invite.isComplete)
        assertEquals(2, invite.maxUses)
    }

    @Test
    fun theCurrentRosterReadsMembersDeviceNamesUnpairedDevicesHubsAndInvites() {
        // Arrange
        val doc = AutomergeFixtures.load("roster-current.automerge")

        // Act
        val members = doc.readRoster().associateBy { it.memberId }
        val invites = doc.readInvites().associateBy { it.secretHash }

        // Assert
        assertEquals(setOf("mum", "dad", "kid"), members.keys)
        assertTrue(members.getValue("kid").removed)
        assertEquals("avatar-1", members.getValue("mum").avatar)
        assertNull(members.getValue("dad").avatar)
        assertEquals(setOf("agent-mum-phone"), members.getValue("mum").devices)
        assertEquals(mapOf("agent-mum-phone" to "Mum's phone"), doc.readRosterDeviceNames())
        assertEquals(mapOf("agent-mum-tablet" to "mum"), doc.readRosterGone())
        assertEquals(setOf("agent-mum-tablet"), doc.readRosterGoneDevices())
        assertEquals("group-1", doc.readRosterGroup())
        assertEquals(setOf("agent-hub", "agent-mum-phone"), doc.readRosterHubs())
        assertEquals(setOf("hash1", "hash2"), invites.keys)
        assertEquals(setOf("dad"), invites.getValue("hash1").usedBy)
        assertTrue(invites.getValue("hash2").revoked)
    }

    @Test
    fun aRosterWithUnknownKeysReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("roster-current.automerge")
        val extras = AutomergeFixtures.load("roster-extra-keys.automerge")

        // Act
        val members = extras.readRoster()

        // Assert
        assertEquals(3, members.size)
        assertEquals(current.readRoster(), members)
        assertEquals(current.readInvites(), extras.readInvites())
        assertEquals(current.readRosterHubs(), extras.readRosterHubs())
        assertEquals(current.readRosterDeviceNames(), extras.readRosterDeviceNames())
        assertEquals(current.readRosterGone(), extras.readRosterGone())
        assertEquals(current.readRosterGroup(), extras.readRosterGroup())
    }

    // ---- Registries ------------------------------------------------------------------------------------------------

    @Test
    fun theCurrentHomeRegistryReadsLiveHomesAndTombstones() {
        // Arrange
        val doc = AutomergeFixtures.load("home-registry-current.automerge")

        // Act
        val homes = doc.readHomeRegistry().associateBy { it.id }

        // Assert
        assertEquals(setOf("home-1", "home-2", "home-3"), homes.keys)
        assertEquals(RegistryHome("home-1", "The Flat", "family", 1_790_000_000_000, false), homes.getValue("home-1"))
        assertTrue(homes.getValue("home-3").deleted)
        assertEquals("couple", homes.getValue("home-2").shape)
    }

    @Test
    fun aHomeRegistryWithUnknownKeysReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("home-registry-current.automerge").readHomeRegistry()
        val extras = AutomergeFixtures.load("home-registry-extra-keys.automerge")

        // Act
        val homes = extras.readHomeRegistry()

        // Assert
        assertEquals(3, homes.size)
        assertEquals(current.toSet(), homes.toSet())
    }

    @Test
    fun theCurrentProfileRegistryReadsNamedAndPlaceholderProfilesAndPicksTheNamedOne() {
        // Arrange
        val doc = AutomergeFixtures.load("profile-registry-current.automerge")

        // Act
        val profiles = doc.readProfiles().associateBy { it.id }

        // Assert
        assertEquals(setOf("p1", "p2"), profiles.keys)
        assertTrue(profiles.getValue("p1").named)
        assertEquals("avatar-1", profiles.getValue("p1").avatar)
        assertFalse(profiles.getValue("p2").named)
        assertNull(profiles.getValue("p2").avatar)
        assertEquals("p1", profiles.values.toList().canonical()?.id)
    }

    @Test
    fun aProfileRegistryWithUnknownKeysReadsTheSameAsWithout() {
        // Arrange
        val current = AutomergeFixtures.load("profile-registry-current.automerge").readProfiles()
        val extras = AutomergeFixtures.load("profile-registry-extra-keys.automerge")

        // Act
        val profiles = extras.readProfiles()

        // Assert
        assertEquals(2, profiles.size)
        assertEquals(current.toSet(), profiles.toSet())
    }

    // ---- A future map at the top of an entry container (documented hazard) -----------------------------------------
    //
    // ADR-034 rule 2 says readers ignore unknown keys. The documents below keep their entries as maps directly under the
    // root (or under `items`/`categories`) and read EVERY map there as an entry, so a future key holding a map would show
    // up on this version as a blank entry. Scalar keys (like `schemaVersion`) are safe. Recorded here, not fixed: WP1 must
    // not put a map at those places, or the readers must learn to skip entries that lack their identifying field.

    private fun Document.withFutureMapAt(parent: ObjectId = ObjectId.ROOT, key: String = "futureMap"): Document {
        val tx = startTransaction()
        tx.set(tx.set(parent, key, ObjectType.MAP), "unknownField", "x")
        tx.commit()
        return this
    }

    @Test
    fun aFutureRootMapShowsUpAsABlankMerchantAndABlankRecipe() {
        // Arrange
        val merchants = AutomergeFixtures.load("merchants-current.automerge").withFutureMapAt()
        val cookbook = AutomergeFixtures.load("cookbook-current.automerge").withFutureMapAt()

        // Act
        val merchantList = merchants.readMerchants()
        val recipeList = cookbook.readCookbook()

        // Assert
        assertEquals(4, merchantList.size, "current behaviour: the unknown map is read as a merchant")
        assertEquals(1, merchantList.count { it.name.isEmpty() })
        assertEquals(4, recipeList.size, "current behaviour: the unknown map is read as a recipe")
        assertEquals(1, recipeList.count { it.title.isEmpty() })
    }

    @Test
    fun aFutureRootMapShowsUpAsABlankHomeAndAnIncompleteProfileButNotAsAnEventOrTask() {
        // Arrange
        val homes = AutomergeFixtures.load("home-registry-current.automerge").withFutureMapAt()
        val profiles = AutomergeFixtures.load("profile-registry-current.automerge").withFutureMapAt()
        val calendar = AutomergeFixtures.load("calendar-current.automerge").withFutureMapAt()
        val tasks = AutomergeFixtures.load("tasks-current.automerge").withFutureMapAt()

        // Act
        val homeList = homes.readHomeRegistry()
        val profileList = profiles.readProfiles()

        // Assert
        assertEquals(4, homeList.size, "current behaviour: the unknown map is read as a Home with a blank name")
        assertEquals(1, homeList.count { it.name.isEmpty() })
        assertEquals(3, profileList.size)
        assertFalse(profileList.first { it.id == "futureMap" }.isComplete)
        assertEquals("p1", profileList.canonical()?.id)
        assertEquals(3, calendar.readCalendarEvents(EventCalendar.Home).size)
        assertEquals(3, tasks.readTasks(TaskList.Home).tasks.size)
    }

    @Test
    fun aFutureMapInsideItemsOrCategoriesShowsUpAsABlankPantryItemAndCategory() {
        // Arrange
        val pantry = AutomergeFixtures.load("pantry-current.automerge")
        val itemsId = (pantry.get(ObjectId.ROOT, "items").getOrNull() as org.automerge.AmValue.Map).id
        val categoriesId = (pantry.get(ObjectId.ROOT, "categories").getOrNull() as org.automerge.AmValue.Map).id
        pantry.withFutureMapAt(itemsId).withFutureMapAt(categoriesId)
        val shopping = AutomergeFixtures.load("shopping-list-current.automerge")
        val shoppingItemsId = (shopping.get(ObjectId.ROOT, "items").getOrNull() as org.automerge.AmValue.Map).id
        shopping.withFutureMapAt(shoppingItemsId)

        // Act
        val items = pantry.readPantry()
        val categories = pantry.readPantryCategories()
        val shoppingItems = shopping.readShoppingList()

        // Assert
        assertEquals(5, items.size, "current behaviour: the unknown map is read as an item with blank fields")
        assertEquals(1, items.count { it.title.isEmpty() })
        assertEquals(23, categories.size, "current behaviour: the unknown map is read as a category with a blank name")
        assertEquals(5, shoppingItems.size)
    }

    @Test
    fun aFutureRootKeyOfAnyTypeIsIgnoredByTheFlatKeyDocuments() {
        // Arrange
        val roster = AutomergeFixtures.load("roster-current.automerge").withFutureMapAt()
        val plan = AutomergeFixtures.load("mealplan-current.automerge").withFutureMapAt()
        val tx = plan.startTransaction()
        tx.set(ObjectId.ROOT, "futureStamp", Date(1))
        tx.commit()

        // Act
        val members = roster.readRoster()
        val meals = plan.readMealPlan()

        // Assert
        assertEquals(3, members.size)
        assertEquals(AutomergeFixtures.load("mealplan-current.automerge").readMealPlan(), meals)
        assertContentEquals(
            AutomergeFixtures.load("roster-current.automerge").readInvites().map { it.secretHash },
            roster.readInvites().map { it.secretHash },
        )
    }
}
