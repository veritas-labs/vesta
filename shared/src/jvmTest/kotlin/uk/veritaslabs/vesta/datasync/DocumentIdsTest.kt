package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.home.homeRepository
import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MealPlanSources
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.calendarDocumentId
import uk.veritaslabs.vesta.automerge.cookbookDocumentId
import uk.veritaslabs.vesta.automerge.mealPlanDocumentId
import uk.veritaslabs.vesta.automerge.merchantsDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.privateCalendarDocumentId
import uk.veritaslabs.vesta.automerge.privateTasksDocumentId
import uk.veritaslabs.vesta.automerge.rosterDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.automerge.tasksDocumentId
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.mailbox.HomeMailboxDocuments
import uk.veritaslabs.vesta.automerge.VestaHomeDocuments
import uk.veritaslabs.vesta.docs.Scope
import uk.veritaslabs.vesta.profile.ProfileRepository

/**
 * Pins what the document registry refactor must not change (docs/plans/document-registry-and-source-collapse-plan.md, WP0):
 * the literal id of each Home document, which Sync activity module it belongs to, whether it is private to the user's own
 * devices, whether the mailbox carries it, and the order the mailbox lists them in. Ids are on the wire and in stored rows,
 * so devices on older versions rely on every one of them.
 */
class DocumentIdsTest {

    private val homeId = "home-1"

    private class Expected(
        val id: String,
        val module: String,
        val private: Boolean,
        val inMailbox: Boolean,
        val idFor: (String) -> String,
    )

    private val ten = listOf(
        Expected("$homeId:pantry", SyncModules.Pantry, private = false, inMailbox = true, idFor = ::pantryDocumentId),
        Expected("$homeId:shopping-list", SyncModules.Shopping, private = false, inMailbox = true, idFor = ::shoppingListDocumentId),
        Expected("$homeId:cookbook", SyncModules.Cookbook, private = false, inMailbox = true, idFor = ::cookbookDocumentId),
        Expected("$homeId:merchants", SyncModules.Merchants, private = false, inMailbox = true, idFor = ::merchantsDocumentId),
        Expected("$homeId:mealplan", SyncModules.MealPlan, private = false, inMailbox = true, idFor = ::mealPlanDocumentId),
        Expected("$homeId:calendar", SyncModules.Calendar, private = false, inMailbox = true, idFor = ::calendarDocumentId),
        Expected("$homeId:private-calendar", SyncModules.Calendar, private = true, inMailbox = false, idFor = ::privateCalendarDocumentId),
        Expected("$homeId:tasks", SyncModules.Tasks, private = false, inMailbox = true, idFor = ::tasksDocumentId),
        Expected("$homeId:private-tasks", SyncModules.Tasks, private = true, inMailbox = false, idFor = ::privateTasksDocumentId),
        Expected("$homeId:roster", SyncModules.Members, private = false, inMailbox = true, idFor = ::rosterDocumentId),
    )

    private fun mailbox(database: VestaDatabase) = HomeMailboxDocuments(documents(database).registry)

    private fun documents(database: VestaDatabase) =
        VestaHomeDocuments(database, HomeRosters(database, ProfileRepository(database, deviceName = ::getDeviceName), deviceName = ::getDeviceName), publishRosters = {})

    private fun changesWaiting(database: VestaDatabase, documents: VestaHomeDocuments) = ChangesWaiting(
        database,
        sharedDocumentIds = { emptyList() },
        privateDocumentIds = { documents.registry.documentIds(it, Scope.Private) },
        targets = { SyncTargets(shared = false, own = false) },
        heads = { _, _ -> null },
    )

    @Test
    fun eachDocumentHasItsLiteralId() {
        // Arrange
        val expected = ten.map { it.id }

        // Act
        val actual = ten.map { it.idFor(homeId) }

        // Assert
        assertEquals(expected, actual)
        assertEquals(10, actual.toSet().size)
    }

    @Test
    fun eachDocumentBelongsToItsSyncActivityModule() {
        // Arrange
        val expected = ten.associate { it.id to it.module }

        // Act
        val actual = ten.associate { it.id to documents(openInMemoryDatabase()).registry.moduleOf(it.idFor(homeId)) }

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun onlyThePrivateCalendarAndPrivateTasksAreKeptToTheUsersOwnDevices() {
        // Arrange
        val database = openInMemoryDatabase()
        val expectedPrivate = ten.filter { it.private }.map { it.id }

        // Act
        val actual = documents(database).registry.documentIds(homeId, Scope.Private)

        // Assert
        assertEquals(expectedPrivate.sorted(), actual.sorted())
        assertEquals(setOf("$homeId:private-calendar", "$homeId:private-tasks"), actual.toSet())
    }

    @Test
    fun theMailboxListsEveryDocumentButThePrivateOnes() {
        // Arrange
        val mailbox = mailbox(openInMemoryDatabase())

        // Act
        val listed = mailbox.documentIds(homeId).toSet()

        // Assert
        ten.forEach { assertEquals(it.inMailbox, it.id in listed, "${it.id} in the mailbox") }
        assertEquals(ten.filter { it.inMailbox }.map { it.id }.toSet(), listed)
    }

    @Test
    fun theMailboxListsExactlyTheRegistrysSharedDocumentsSoNoTypeCanBeLeftOut() {
        // Arrange
        val documents = documents(openInMemoryDatabase())
        val mailbox = HomeMailboxDocuments(documents.registry)

        // Act
        val listed = mailbox.documentIds(homeId)

        // Assert
        assertEquals(documents.registry.documentIds(homeId, Scope.Shared), listed)
        assertEquals(8, listed.size)
    }

    @Test
    fun theMailboxListsTheRosterFirst() {
        // Arrange
        val mailbox = mailbox(openInMemoryDatabase())

        // Act
        val listed = mailbox.documentIds(homeId)

        // Assert
        assertEquals(
            listOf(
                "$homeId:roster", "$homeId:pantry", "$homeId:shopping-list", "$homeId:cookbook",
                "$homeId:merchants", "$homeId:mealplan", "$homeId:calendar", "$homeId:tasks",
            ),
            listed,
        )
    }

    @Test
    fun aHomeIdWithoutAColonIsWhatTheIdsAssume() = runBlocking {
        // Arrange
        val homes = homeRepository(openInMemoryDatabase())
        val generated = (1..50).map { homes.create("Home $it", HomeShape.Family.storageValue) }

        // Act
        val withColon = generated.filter { ':' in it }

        // Assert
        assertTrue(withColon.isEmpty(), "home ids containing ':' $withColon")
    }
}
