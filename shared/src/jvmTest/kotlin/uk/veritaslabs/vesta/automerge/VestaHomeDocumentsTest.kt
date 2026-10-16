package uk.veritaslabs.vesta.automerge

import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.datasync.HomeRosters
import uk.veritaslabs.vesta.datasync.TestHomeDocuments
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.docs.Scope
import uk.veritaslabs.vesta.profile.ProfileRepository

class VestaHomeDocumentsTest {

    private val features = listOf(
        "roster", "pantry", "shopping-list", "cookbook", "merchants", "mealplan", "calendar", "tasks",
        "private-calendar", "private-tasks",
    )

    private fun documents(database: uk.veritaslabs.kits.store.KitsDatabase) =
        TestHomeDocuments(database, HomeRosters(database, ProfileRepository(database, deviceName = ::getDeviceName), deviceName = ::getDeviceName), publishRosters = {})

    @Test
    fun registersTheTenTypesUnderTheirWireFeatureKeys() {
        // Arrange
        val documents = documents(runBlocking { openInMemoryDatabase() })

        // Act
        val ids = documents.registry.documentIds("home-1")

        // Assert
        assertEquals(features.map { "home-1:$it" }, ids)
        assertEquals(listOf("private-calendar", "private-tasks"), documents.registry.types.filter { it.scope == Scope.Private }.map { it.feature })
    }

    @Test
    fun everyTypeOpensADocumentForAnExistingHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val documents = documents(database)

        // Act
        val opened = features.associateWith { documents.registry.open("home-1", "home-1:$it") }

        // Assert
        opened.forEach { (feature, document) -> assertNotNull(document, "no document opened for $feature") }
    }

    @Test
    fun everyTypeReturnsNullForAHomeThatDoesNotExist() = runBlocking {
        // Arrange
        val documents = documents(openInMemoryDatabase())

        // Act
        val opened = features.associateWith { documents.registry.open("missing", "missing:$it") }

        // Assert
        opened.forEach { (feature, document) -> assertNull(document, "document opened for $feature") }
    }
}
