package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.Merchant

class MerchantsDocumentSourceTest {

    private fun newSource(persist: suspend (ByteArray) -> Unit = {}, onLocalChange: () -> Unit = {}) =
        MerchantsDocumentSource(Document(), persist, onLocalChange)

    @Test
    fun aSavedNewMerchantIsPublishedUnderTheKeyReturned() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        val key = source.save(Merchant(name = "Aldi"))
        val merchants = source.merchants.first()

        // Assert
        assertEquals(listOf(key to "Aldi"), merchants.map { it.key to it.name })
    }

    @Test
    fun savingTwoNewMerchantsGivesThemDifferentKeys() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        val first = source.save(Merchant(name = "Aldi"))
        val second = source.save(Merchant(name = "Aldi"))

        // Assert
        assertNotEquals(first, second)
        assertEquals(2, source.merchants.first().size)
    }

    @Test
    fun savingAMerchantWithAKeyReplacesThatMerchant() = runBlocking {
        // Arrange
        val source = newSource()
        val key = source.save(Merchant(name = "Aldi"))

        // Act
        source.save(Merchant(key = key, name = "Aldi Local"))
        val merchants = source.merchants.first()

        // Assert
        assertEquals(listOf("Aldi Local"), merchants.map { it.name })
    }

    @Test
    fun removingAMerchantIsPublished() = runBlocking {
        // Arrange
        val source = newSource()
        val key = source.save(Merchant(name = "Aldi"))
        source.save(Merchant(name = "Booths"))

        // Act
        source.remove(key)

        // Assert
        assertEquals(listOf("Booths"), source.merchants.first().map { it.name })
    }

    @Test
    fun everyLocalEditIsPersistedAndReported() = runBlocking {
        // Arrange
        var saves = 0
        var changes = 0
        val source = newSource(persist = { saves++ }, onLocalChange = { changes++ })

        // Act
        val key = source.save(Merchant(name = "Aldi"))
        source.remove(key)

        // Assert
        assertEquals(2, saves)
        assertEquals(2, changes)
    }

    @Test
    fun aChangeSyncedInFromAnotherDeviceIsPublishedButNotReportedAsLocal() = runBlocking {
        // Arrange
        var changes = 0
        val ours = newSource(onLocalChange = { changes++ })
        val theirs = newSource()
        theirs.save(Merchant(name = "Their market stall"))

        // Act
        ours.applyChanges(theirs.changesSince(ByteArray(0)))

        // Assert
        assertEquals(listOf("Their market stall"), ours.merchants.first().map { it.name })
        assertEquals(0, changes)
    }

    @Test
    fun reopeningAHomesMerchantsRestoresThem() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        openMerchantsDocumentSource(database, "home-1").save(Merchant(name = "Aldi"))

        // Act
        val reopened = openMerchantsDocumentSource(database, "home-1")

        // Assert
        assertEquals(listOf("Aldi"), reopened.merchants.first().map { it.name })
    }

    @Test
    fun aNewMerchantsDocumentIsStoredImmediatelyAndRemovedWithItsHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)

        // Act
        openMerchantsDocumentSource(database, "home-1")
        val before = database.automergeDocumentQueries.selectById(merchantsDocumentId("home-1")).awaitAsOneOrNull()
        database.homeQueries.deleteById("home-1")
        val after = database.automergeDocumentQueries.selectById(merchantsDocumentId("home-1")).awaitAsOneOrNull()

        // Assert
        assertEquals("merchants", before?.feature)
        assertNull(after)
    }

    @Test
    fun theSourcesShareOneSourcePerHomeAndNoneForAnUnknownHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sources = MerchantsSources(database)

        // Act
        val first = sources.forHome("home-1")
        val second = sources.forHome("home-1")
        val unknown = sources.forHome("nope")

        // Assert
        assertEquals(true, first === second)
        assertNull(unknown)
    }

    @Test
    fun aLocalEditIsAnnouncedWithItsHomesId() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sources = MerchantsSources(database)
        val announced = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined) { sources.localChanges.collect { announced += it } }

        // Act
        sources.forHome("home-1")!!.save(Merchant(name = "Aldi"))
        collector.cancel()

        // Assert
        assertEquals(listOf("home-1"), announced)
    }
}
