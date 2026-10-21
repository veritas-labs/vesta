package uk.veritaslabs.vesta.acceptance

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.automerge.SyncState
import uk.veritaslabs.kits.KitsInternalApi
import uk.veritaslabs.kits.spaces.SpaceAttributes
import uk.veritaslabs.kits.spaces.SpaceRegistry
import uk.veritaslabs.kits.spaces.SpaceSeeding
import uk.veritaslabs.kits.spaces.StoredSpace
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.autonomi.AutonomiClient
import uk.veritaslabs.vesta.autonomi.AutonomiStatus
import uk.veritaslabs.vesta.location.AutonomiLocalityPackSource
import uk.veritaslabs.vesta.location.ChunkCache
import uk.veritaslabs.vesta.location.LocalityPackConfig
import uk.veritaslabs.vesta.location.RegistryHomeLocationRepository
import uk.veritaslabs.vesta.location.TinyPack
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.ui.screens.AreaFakeRepository
import uk.veritaslabs.vesta.ui.screens.AreaStep
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaViewModel

/**
 * Marketplace location and search area (ADR-039). Acceptance criteria are the numbered list in the ADR's
 * "Acceptance criteria" section and are mapped, with the tests that back each one, in docs/testing/acceptance-tests.md.
 * This class holds the criteria no other test proves end to end: the area view model on the real Autonomi pack source over
 * a recording client (1, 4, and the shape of 7), the saved shape on the real registry (6) and forgetting on a paired
 * device (9). It does not drive the screens; those are in MarketplaceAreaFlowTest.
 */
@OptIn(ExperimentalCoroutinesApi::class, KitsInternalApi::class)
class MarketplaceAreaAcceptanceTest {

    private val rootAddress = "cd".repeat(32)
    private val namesAddress = "ab".repeat(32)
    private val adjacencyAddress = "01".repeat(32)

    /** Records every request made to the network, by kind and address. */
    private class RecordingClient(val chunks: Map<String, ByteArray>) : AutonomiClient {
        val requests = mutableListOf<String>()
        override val endpoint = "fake"
        override val supportsChunks = true
        override suspend fun connectionStatus(): AutonomiStatus? = null
        override suspend fun chunkGet(addressHex: String): ByteArray? = chunks[addressHex].also { requests += "chunk $addressHex" }
        override suspend fun pointerResolve(addressHex: String): String? = null.also { requests += "pointer $addressHex" }
    }

    private class MemoryCache : ChunkCache {
        val entries = mutableMapOf<String, ByteArray>()
        override fun get(address: String) = entries[address]
        override fun put(address: String, bytes: ByteArray) {
            entries[address] = bytes
        }
    }

    private fun client() = RecordingClient(
        mapOf(
            rootAddress to TinyPack.rootChunk(),
            namesAddress to TinyPack.namesChunk(),
            adjacencyAddress to TinyPack.adjacencyChunk(),
        ),
    )

    private fun TestScope.viewModel(client: RecordingClient, repository: AreaFakeRepository = AreaFakeRepository()): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val source = AutonomiLocalityPackSource(client, LocalityPackConfig(pointer = null, root = rootAddress), MemoryCache(), TinyPack.identity)
        return MarketplaceAreaViewModel("home", source, repository) { "GB" }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun withNoLocationSetAndNotNowNothingIsRequestedFromTheNetwork() = runTest {
        // Arrange — Given a Home with no location and nothing downloaded, when I open Nearby merchants I am asked first
        val client = client()
        val viewModel = viewModel(client)
        viewModel.start()
        assertEquals(AreaStep.Disclosure, viewModel.uiState.value.step)

        // Act — When I choose Not now
        viewModel.declineDownload()

        // Assert — Then no request of any kind was made, and the Home has no location saved
        assertEquals(emptyList(), client.requests)
        assertEquals(AreaStep.Declined, viewModel.uiState.value.step)
    }

    @Test
    fun onlyChunkReadsByAddressAreMadeAfterAgreeingAndATypedQueryIsNeverSent() = runTest {
        // Arrange — Given I agreed to the download and am on the search step
        val client = client()
        val viewModel = viewModel(client)
        viewModel.start()
        viewModel.agreeToDownload()
        viewModel.selectCountry("GB")
        viewModel.continueFromCountry()
        val requestsAfterDownload = client.requests.toList()

        // Act — When I type a town name and a postcode-shaped query
        viewModel.setQuery("newport")
        viewModel.setQuery("PO30 1AA")

        // Assert — Then every request was a read of a fixed chunk address, none carries what I typed, and typing made none
        assertTrue(requestsAfterDownload.isNotEmpty())
        assertTrue(requestsAfterDownload.all { it.startsWith("chunk ") })
        assertEquals(requestsAfterDownload, client.requests)
        assertTrue(client.requests.none { it.contains("newport", ignoreCase = true) || it.contains("PO30", ignoreCase = true) })
    }

    @Test
    fun whatIsSavedIsTheCountryTheLocalityTheReleaseAndTheAreaAndNoPosition() = runBlocking {
        // Arrange — Given a Home in the registry
        val registry = SpaceRegistry(openInMemoryDatabase(), seeding)
        registry.saved(StoredSpace("h1", "Home", "family", 1L))
        val repository = RegistryHomeLocationRepository(registry.asAttributes())
        val before = registry.attributes("h1").keys

        // Act — When I save a home locality and two search-area localities
        repository.setHome("h1", "GB", id(3), "2026-09-23.1")
        repository.setArea("h1", setOf(id(5), id(13)))

        // Assert — Then only location attributes were added, none is a coordinate, and no cell was stored
        val added = registry.attributes("h1").filterKeys { it !in before }
        assertTrue(added.isNotEmpty())
        assertTrue(added.keys.none { key -> listOf("lat", "lon", "coord", "position").any { key.contains(it, ignoreCase = true) } })
        assertTrue(added.values.none { it.matches(Regex("-?\\d+\\.\\d+")) })
        val location = assertNotNull(repository.read("h1"))
        assertEquals("GB", location.country)
        assertEquals(id(3), location.homeLocality)
        assertEquals("2026-09-23.1", location.release)
        assertEquals(setOf(id(5), id(13)), location.area)
        assertNull(location.cell)
        assertNull(location.cellSource)
    }

    @Test
    fun forgettingTheLocationOnOneDeviceRemovesItFromMyPairedDevice() = runBlocking {
        // Arrange — Given a location set on my phone and seen on my tablet
        val phone = SpaceRegistry(openInMemoryDatabase(), seeding)
        val tablet = SpaceRegistry(openInMemoryDatabase(), seeding)
        phone.saved(StoredSpace("h1", "Home", "family", 1L))
        val phoneState = SyncState()
        val tabletState = SyncState()
        suspend fun sync() {
            repeat(6) {
                phone.generate(phoneState)?.let { tablet.receive(tabletState, it) }
                tablet.generate(tabletState)?.let { phone.receive(phoneState, it) }
            }
        }
        sync()
        val onPhone = RegistryHomeLocationRepository(phone.asAttributes())
        val keysBefore = tablet.attributes("h1").keys
        val onTablet = RegistryHomeLocationRepository(tablet.asAttributes())
        onPhone.setHome("h1", "GB", id(3), "2026-09-23.1")
        onPhone.setArea("h1", setOf(id(5)))
        sync()
        assertNotNull(onTablet.read("h1"))

        // Act — When I forget the location on my phone and the devices sync
        onPhone.clear("h1")
        sync()

        // Assert — Then my tablet has no location for the Home either, and the Home carries nothing it did not before
        assertNull(onTablet.read("h1"))
        assertNull(onPhone.read("h1"))
        assertEquals(keysBefore, tablet.attributes("h1").keys)
    }

    private val seeding = object : SpaceSeeding {
        override suspend fun seedCreated(spaceId: String) = Unit
        override suspend fun placeholdersForArrived(spaceId: String) = Unit
    }

    private fun SpaceRegistry.asAttributes() = object : SpaceAttributes {
        override suspend fun attributes(spaceId: String) = this@asAttributes.attributes(spaceId)
        override suspend fun setAttributes(spaceId: String, changes: Map<String, String?>) = this@asAttributes.setAttributes(spaceId, changes)
    }
}
