package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.MealSlot

class MealPlanDocumentSourceTest {

    private val monday = CalendarDate(2026, 10, 12)
    private val tuesday = CalendarDate(2026, 10, 13)

    /** A clock that ticks one millisecond each time it is read, so recipes planned in turn are stamped in turn. */
    private fun ticking(): () -> Long {
        var now = 1_000L
        return { now++ }
    }

    private fun newSource(
        document: Document = Document(),
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
    ) = MealPlanDocumentSource(document, persist, onLocalChange, ticking())

    @Test
    fun aPlannedRecipeIsPublishedForItsDayAndSlot() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.add(monday, MealSlot.Dinner, "pie")
        val plan = source.plan.first()

        // Assert
        assertEquals(listOf("pie"), plan.recipes(monday, MealSlot.Dinner))
        assertEquals(emptyList(), plan.recipes(monday, MealSlot.Lunch))
        assertEquals(emptyList(), plan.recipes(tuesday, MealSlot.Dinner))
    }

    @Test
    fun aSlotListsItsRecipesInTheOrderTheyWereAdded() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.add(monday, MealSlot.Dinner, "zebra-cake")
        source.add(monday, MealSlot.Dinner, "apple-pie")
        source.add(monday, MealSlot.Dinner, "mash")

        // Assert
        assertEquals(listOf("zebra-cake", "apple-pie", "mash"), source.plan.first().recipes(monday, MealSlot.Dinner))
    }

    @Test
    fun planningTheSameRecipeTwiceInASlotKeepsOneAndDoesNotReportAChange() = runBlocking {
        // Arrange
        var changes = 0
        val source = newSource(onLocalChange = { changes++ })
        source.add(monday, MealSlot.Lunch, "soup")

        // Act
        source.add(monday, MealSlot.Lunch, "soup")

        // Assert
        assertEquals(listOf("soup"), source.plan.first().recipes(monday, MealSlot.Lunch))
        assertEquals(1, changes)
    }

    @Test
    fun theSameRecipeCanBePlannedInDifferentSlotsAndDays() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.add(monday, MealSlot.Lunch, "soup")
        source.add(monday, MealSlot.Dinner, "soup")
        source.add(tuesday, MealSlot.Lunch, "soup")
        val plan = source.plan.first()

        // Assert
        assertEquals(listOf("soup"), plan.recipes(monday, MealSlot.Lunch))
        assertEquals(listOf("soup"), plan.recipes(monday, MealSlot.Dinner))
        assertEquals(listOf("soup"), plan.recipes(tuesday, MealSlot.Lunch))
    }

    @Test
    fun removingARecipeTakesItOutOfThatSlotOnly() = runBlocking {
        // Arrange
        val source = newSource()
        source.add(monday, MealSlot.Lunch, "soup")
        source.add(monday, MealSlot.Dinner, "soup")

        // Act
        source.remove(monday, MealSlot.Lunch, "soup")
        val plan = source.plan.first()

        // Assert
        assertEquals(emptyList(), plan.recipes(monday, MealSlot.Lunch))
        assertEquals(listOf("soup"), plan.recipes(monday, MealSlot.Dinner))
    }

    @Test
    fun removingARecipeThatIsNotPlannedChangesNothingAndIsNotReported() = runBlocking {
        // Arrange
        var changes = 0
        val source = newSource(onLocalChange = { changes++ })

        // Act
        source.remove(monday, MealSlot.Lunch, "soup")

        // Assert
        assertEquals(0, changes)
    }

    @Test
    fun everyLocalEditIsPersistedAndReported() = runBlocking {
        // Arrange
        var saves = 0
        var changes = 0
        val source = newSource(persist = { saves++ }, onLocalChange = { changes++ })

        // Act
        source.add(monday, MealSlot.Lunch, "soup")
        source.remove(monday, MealSlot.Lunch, "soup")

        // Assert
        assertEquals(2, saves)
        assertEquals(2, changes)
    }

    @Test
    fun twoDevicesPlanningTheSameNewDayAtOnceKeepBothRecipes() = runBlocking {
        // Arrange — two devices that start from the same empty plan
        val base = Document().save()
        val a = newSource(Document.load(base))
        val b = newSource(Document.load(base))

        // Act — each plans a recipe for the same new day and slot, then they exchange changes
        a.add(monday, MealSlot.Dinner, "pie")
        b.add(monday, MealSlot.Dinner, "curry")
        a.applyChanges(b.changesSince(ByteArray(0)))
        b.applyChanges(a.changesSince(ByteArray(0)))

        // Assert
        val expected = setOf("pie", "curry")
        assertEquals(expected, a.plan.first().recipes(monday, MealSlot.Dinner).toSet())
        assertEquals(expected, b.plan.first().recipes(monday, MealSlot.Dinner).toSet())
    }

    @Test
    fun aKeyThisVersionCannotReadIsSkippedRatherThanBreakingThePlan() = runBlocking {
        // Arrange — a document with a future slot and a damaged date alongside a good entry
        val document = Document()
        val tx = document.startTransaction()
        tx.set(org.automerge.ObjectId.ROOT, "2026-10-12|Supper|pie", java.util.Date(1L))
        tx.set(org.automerge.ObjectId.ROOT, "not-a-date|Dinner|pie", java.util.Date(1L))
        tx.set(org.automerge.ObjectId.ROOT, "2026-02-30|Dinner|pie", java.util.Date(1L))
        tx.commit()
        val source = newSource(document)
        source.add(monday, MealSlot.Dinner, "stew")

        // Act
        val plan = source.plan.first()

        // Assert
        assertEquals(mapOf(monday to mapOf(MealSlot.Dinner to listOf("stew"))), plan.days)
    }

    @Test
    fun aPlanSurvivesReopening() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 1L)
        openMealPlanDocumentSource(database, "home-1").add(monday, MealSlot.Breakfast, "porridge")

        // Act
        val reopened = openMealPlanDocumentSource(database, "home-1")

        // Assert
        assertEquals(listOf("porridge"), reopened.plan.first().recipes(monday, MealSlot.Breakfast))
    }

    @Test
    fun openingAHomesPlanStoresAnEmptyDocumentUnderItsId() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 1L)

        // Act
        openMealPlanDocumentSource(database, "home-1")

        // Assert
        assertTrue(database.automergeDocumentQueries.selectById("home-1:mealplan").awaitAsOneOrNull() != null)
    }

    @Test
    fun aRecipeNobodyHasChosenEatersForHasNoChosenEaters() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.add(monday, MealSlot.Dinner, "pie")

        // Assert
        assertEquals(null, source.plan.first().chosenEaters(monday, MealSlot.Dinner, "pie"))
    }

    @Test
    fun chosenEatersArePublishedForThatRecipeOnly() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark", "p:ella"))
        val plan = source.plan.first()

        // Assert
        assertEquals(setOf("m:mark", "p:ella"), plan.chosenEaters(monday, MealSlot.Dinner, "pie"))
        assertEquals(null, plan.chosenEaters(monday, MealSlot.Dinner, "curry"))
        assertEquals(null, plan.chosenEaters(monday, MealSlot.Lunch, "pie"))
        assertEquals(null, plan.chosenEaters(tuesday, MealSlot.Dinner, "pie"))
    }

    @Test
    fun choosingNobodyIsDifferentFromNotHavingChosen() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", emptySet())

        // Assert
        assertEquals(emptySet(), source.plan.first().chosenEaters(monday, MealSlot.Dinner, "pie"))
    }

    @Test
    fun changingTheEatersReplacesThem() = runBlocking {
        // Arrange
        val source = newSource()
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark", "p:ella"))

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark", "m:sam"))

        // Assert
        assertEquals(setOf("m:mark", "m:sam"), source.plan.first().chosenEaters(monday, MealSlot.Dinner, "pie"))
    }

    @Test
    fun choosingTheSameEatersAgainIsNotReportedAsAChange() = runBlocking {
        // Arrange
        var changes = 0
        val source = newSource(onLocalChange = { changes++ })
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark"))

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark"))

        // Assert
        assertEquals(1, changes)
    }

    @Test
    fun eatersDoNotDisturbThePlannedRecipes() = runBlocking {
        // Arrange
        val source = newSource()
        source.add(monday, MealSlot.Dinner, "pie")

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark"))
        source.addGuest("Ella")
        val plan = source.plan.first()

        // Assert
        assertEquals(mapOf(monday to mapOf(MealSlot.Dinner to listOf("pie"))), plan.days)
    }

    @Test
    fun aGuestIsAddedRenamedAndRemoved() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        val id = source.addGuest("  Ella ")!!
        val added = source.plan.first().guests
        source.renameGuest(id, "Ellie")
        val renamed = source.plan.first().guests
        source.removeGuest(id)
        val removed = source.plan.first().guests

        // Assert
        assertEquals(listOf("Ella"), added.map { it.name })
        assertEquals(listOf(id to "Ellie"), renamed.map { it.id to it.name })
        assertEquals(emptyList(), removed)
    }

    @Test
    fun aBlankGuestNameAddsNothing() = runBlocking {
        // Arrange
        var changes = 0
        val source = newSource(onLocalChange = { changes++ })

        // Act
        val id = source.addGuest("   ")

        // Assert
        assertEquals(null, id)
        assertEquals(0, changes)
        assertEquals(emptyList(), source.plan.first().guests)
    }

    @Test
    fun guestsAreListedByName() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        source.addGuest("noah")
        source.addGuest("Ella")

        // Assert
        assertEquals(listOf("Ella", "noah"), source.plan.first().guests.map { it.name })
    }

    @Test
    fun twoDevicesAddingGuestsAtOnceKeepBoth() = runBlocking {
        // Arrange
        val base = Document().save()
        val a = newSource(Document.load(base))
        val b = newSource(Document.load(base))

        // Act
        a.addGuest("Ella")
        b.addGuest("Noah")
        a.applyChanges(b.changesSince(ByteArray(0)))
        b.applyChanges(a.changesSince(ByteArray(0)))

        // Assert
        assertEquals(listOf("Ella", "Noah"), a.plan.first().guests.map { it.name })
        assertEquals(listOf("Ella", "Noah"), b.plan.first().guests.map { it.name })
    }

    @Test
    fun takingARecipeOutTakesItsEatersWithIt() = runBlocking {
        // Arrange
        val source = newSource()
        source.add(monday, MealSlot.Dinner, "pie")
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark"))

        // Act
        source.remove(monday, MealSlot.Dinner, "pie")
        source.add(monday, MealSlot.Dinner, "pie")

        // Assert
        assertEquals(null, source.plan.first().chosenEaters(monday, MealSlot.Dinner, "pie"))
    }

    @Test
    fun twoRecipesInOneSlotHaveTheirOwnEaters() = runBlocking {
        // Arrange
        val source = newSource()
        source.add(monday, MealSlot.Dinner, "pie")
        source.add(monday, MealSlot.Dinner, "mash")

        // Act
        source.setEaters(monday, MealSlot.Dinner, "pie", setOf("m:mark"))
        source.setEaters(monday, MealSlot.Dinner, "mash", setOf("p:ella"))
        val plan = source.plan.first()

        // Assert
        assertEquals(setOf("m:mark"), plan.chosenEaters(monday, MealSlot.Dinner, "pie"))
        assertEquals(setOf("p:ella"), plan.chosenEaters(monday, MealSlot.Dinner, "mash"))
        assertEquals(listOf("pie", "mash"), plan.recipes(monday, MealSlot.Dinner))
    }
}
