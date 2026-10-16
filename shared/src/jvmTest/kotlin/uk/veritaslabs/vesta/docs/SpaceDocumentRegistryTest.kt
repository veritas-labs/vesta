@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.docs

import uk.veritaslabs.kits.docs.Scope
import uk.veritaslabs.kits.docs.SpaceDocument
import uk.veritaslabs.kits.docs.SpaceDocumentRegistry
import uk.veritaslabs.kits.docs.SpaceDocumentType
import uk.veritaslabs.kits.docs.changesSince

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.automerge.SyncState
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape

class SpaceDocumentRegistryTest {

    private object Stub : SpaceDocument {
        override suspend fun generateSync(state: SyncState): ByteArray? = null
        override suspend fun receiveSync(state: SyncState, message: ByteArray) = Unit
        override suspend fun isInSync(state: SyncState) = true
        override suspend fun heads() = ByteArray(0)
        override suspend fun changesSince(saved: ByteArray) = ByteArray(0)
        override suspend fun applyChanges(changes: ByteArray) = Unit
    }

    private fun type(feature: String, scope: Scope = Scope.Shared, open: suspend (String) -> SpaceDocument? = { Stub }) =
        SpaceDocumentType(feature, module = "m-$feature", scope = scope, open = open, localChanges = emptyFlow())

    private val registry = SpaceDocumentRegistry(
        listOf(type("pantry"), type("private-tasks", Scope.Private), type("shopping-list")),
    )

    @Test
    fun parsesASharedId() {
        // Arrange / Act
        val parsed = registry.parse("home-1:pantry")

        // Assert
        assertEquals("home-1", parsed?.spaceId)
        assertEquals("pantry", parsed?.type?.feature)
    }

    @Test
    fun parsesAPrivateIdAndAHyphenatedFeature() {
        // Arrange / Act
        val private = registry.parse("home-1:private-tasks")
        val hyphenated = registry.parse("home-1:shopping-list")

        // Assert
        assertEquals(Scope.Private, private?.type?.scope)
        assertEquals("shopping-list", hyphenated?.type?.feature)
    }

    @Test
    fun splitsOnTheLastColonWhenTheHomeIdContainsOne() {
        // Arrange / Act
        val parsed = registry.parse("a:b:pantry")

        // Assert
        assertEquals("a:b", parsed?.spaceId)
        assertEquals("pantry", parsed?.type?.feature)
    }

    @Test
    fun anUnknownFeatureIsNull() {
        // Arrange / Act
        val parsed = registry.parse("home-1:nonsense")

        // Assert
        assertNull(parsed)
    }

    @Test
    fun anIdWithNoColonIsNull() {
        // Arrange / Act
        val parsed = registry.parse("pantry")

        // Assert
        assertNull(parsed)
    }

    @Test
    fun rejectsDuplicateFeatureKeys() {
        // Arrange / Act / Assert
        assertFailsWith<IllegalArgumentException> { SpaceDocumentRegistry(listOf(type("pantry"), type("pantry"))) }
    }

    @Test
    fun rejectsFeatureKeysContainingAColon() {
        // Arrange / Act / Assert
        assertFailsWith<IllegalArgumentException> { SpaceDocumentRegistry(listOf(type("a:b"))) }
    }

    @Test
    fun documentIdsKeepRegistrationOrderAndFilterByScope() {
        // Arrange / Act
        val all = registry.documentIds("h")
        val shared = registry.documentIds("h", Scope.Shared)
        val private = registry.documentIds("h", Scope.Private)

        // Assert
        assertEquals(listOf("h:pantry", "h:private-tasks", "h:shopping-list"), all)
        assertEquals(listOf("h:pantry", "h:shopping-list"), shared)
        assertEquals(listOf("h:private-tasks"), private)
    }

    @Test
    fun moduleOfReturnsTheTypesModuleOrNull() {
        // Arrange / Act
        val known = registry.moduleOf("h:pantry")
        val unknown = registry.moduleOf("user:homes")

        // Assert
        assertEquals("m-pantry", known)
        assertNull(unknown)
    }

    @Test
    fun openDelegatesToTheTypeWithTheHomeId() = runTest {
        // Arrange
        var opened: String? = null
        val sut = SpaceDocumentRegistry(listOf(type("pantry") { opened = it; Stub }))

        // Act
        val document = sut.open("home-1", "home-1:pantry")

        // Assert
        assertSame(Stub, document)
        assertEquals("home-1", opened)
    }

    @Test
    fun openReturnsNullForAnUnknownIdAndWhenTheTypeHasNoDocument() = runTest {
        // Arrange
        val sut = SpaceDocumentRegistry(listOf(type("pantry") { null }))

        // Act
        val unknown = sut.open("home-1", "home-1:nonsense")
        val absent = sut.open("home-1", "home-1:pantry")

        // Assert
        assertNull(unknown)
        assertNull(absent)
    }

    @Test
    fun generatedHomeIdsNeverContainAColon() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val homes = HomeRepository(db, profiles = ProfileRepository(db, deviceName = { "Mark" }))

        // Act
        val ids = List(50) { homes.create("Home $it", HomeShape.Flatshare.storageValue) }

        // Assert
        assertTrue(ids.isNotEmpty() && ids.none { ':' in it })
    }
}
