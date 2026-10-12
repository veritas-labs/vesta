package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantService
import uk.veritaslabs.vesta.ui.screens.MerchantType
import uk.veritaslabs.vesta.ui.screens.OpenRange
import uk.veritaslabs.vesta.ui.screens.OpeningHours
import uk.veritaslabs.vesta.ui.screens.Weekday

class MerchantsDocumentTest {

    private val butcher = Merchant(
        name = "Hargreaves & Son",
        type = MerchantType.IndependentTrader,
        services = listOf(MerchantService.Butcher, MerchantService.Deli),
        customServices = listOf("Game in season"),
        hours = OpeningHours(
            days = mapOf(
                Weekday.Monday to listOf(OpenRange(9 * 60, 13 * 60), OpenRange(14 * 60, 17 * 60 + 30)),
                Weekday.Saturday to listOf(OpenRange(8 * 60, 13 * 60)),
            ),
            note = "Closed bank holidays",
        ),
        latitude = 53.4808,
        longitude = -2.2426,
        address = "12 Market Street",
        gersId = "08f194ad3a5a5c1b0200d4a3f2c1e9a7",
        x0xAgentId = "ab".repeat(32),
    )

    @Test
    fun anAddedMerchantReadsBackWithEveryField() {
        // Arrange
        val doc = Document()

        // Act
        addMerchant(doc, "m1", butcher)
        val read = doc.readMerchants()

        // Assert
        assertEquals(listOf(butcher.copy(key = "m1")), read)
    }

    @Test
    fun merchantsAreReadOrderedByNameIgnoringCase() {
        // Arrange
        val doc = Document()
        addMerchant(doc, "a", Merchant(name = "tesco"))
        addMerchant(doc, "b", Merchant(name = "Aldi"))
        addMerchant(doc, "c", Merchant(name = "Booths"))

        // Act
        val names = doc.readMerchants().map { it.name }

        // Assert
        assertEquals(listOf("Aldi", "Booths", "tesco"), names)
    }

    @Test
    fun updatingReplacesEveryFieldAndRemovesACoordinateThatWasCleared() {
        // Arrange
        val doc = Document()
        addMerchant(doc, "m1", butcher)

        // Act
        val updated = updateMerchant(doc, "m1", Merchant(name = "Hargreaves", type = MerchantType.LocalChain))
        val read = doc.readMerchants().single()

        // Assert
        assertTrue(updated)
        assertEquals(Merchant(key = "m1", name = "Hargreaves", type = MerchantType.LocalChain), read)
    }

    @Test
    fun updatingAMerchantThatIsNotThereChangesNothing() {
        // Arrange
        val doc = Document()

        // Act
        val updated = updateMerchant(doc, "missing", butcher)

        // Assert
        assertFalse(updated)
        assertEquals(emptyList(), doc.readMerchants())
    }

    @Test
    fun removingAMerchantDeletesOnlyThatOne() {
        // Arrange
        val doc = Document()
        addMerchant(doc, "m1", Merchant(name = "One"))
        addMerchant(doc, "m2", Merchant(name = "Two"))

        // Act
        removeMerchant(doc, "m1")
        removeMerchant(doc, "never-there")

        // Assert
        assertEquals(listOf("Two"), doc.readMerchants().map { it.name })
    }

    @Test
    fun invalidRangesAreDroppedAndEachDaysRangesAreSortedWhenSaved() {
        // Arrange
        val doc = Document()
        val hours = OpeningHours(
            mapOf(
                Weekday.Friday to listOf(OpenRange(15 * 60, 18 * 60), OpenRange(9 * 60, 12 * 60), OpenRange(10 * 60, 10 * 60), OpenRange(-5, 60)),
                Weekday.Sunday to listOf(OpenRange(20 * 60, 19 * 60)),
            ),
        )

        // Act
        addMerchant(doc, "m1", Merchant(name = "Shop", hours = hours))
        val read = doc.readMerchants().single().hours

        // Assert
        assertEquals(listOf(OpenRange(9 * 60, 12 * 60), OpenRange(15 * 60, 18 * 60)), read.on(Weekday.Friday))
        assertEquals(emptyList(), read.on(Weekday.Sunday))
    }

    @Test
    fun aLoneCoordinateIsNotKeptBecauseALocationNeedsBoth() {
        // Arrange
        val doc = Document()

        // Act
        addMerchant(doc, "m1", Merchant(name = "Shop", latitude = 51.5))
        val read = doc.readMerchants().single()

        // Assert
        assertNull(read.latitude)
        assertNull(read.longitude)
    }

    @Test
    fun coordinatesKeepSixDecimalPlaces() {
        // Arrange
        val doc = Document()

        // Act
        addMerchant(doc, "m1", Merchant(name = "Shop", latitude = -33.865143, longitude = 151.2099))
        val read = doc.readMerchants().single()

        // Assert
        assertEquals(-33.865143, read.latitude)
        assertEquals(151.2099, read.longitude)
    }

    @Test
    fun serviceTagsMatchingAKnownServiceBecomeThatServiceAndDuplicatesCollapse() {
        // Arrange
        val doc = Document()

        // Act
        addMerchant(doc, "m1", Merchant(name = "Shop", services = listOf(MerchantService.Bakery), customServices = listOf("bakery", "Wine", " wine ", "")))
        val read = doc.readMerchants().single()

        // Assert
        assertEquals(listOf(MerchantService.Bakery), read.services)
        assertEquals(listOf("Wine"), read.customServices)
    }

    @Test
    fun anUnknownTypeReadsAsOther() {
        // Arrange
        val doc = Document()
        addMerchant(doc, "m1", Merchant(name = "Shop"))
        val tx = doc.startTransaction()
        tx.set((tx.get(ObjectId.ROOT, "m1").get() as AmValue.Map).id, "type", "FutureType")
        tx.commit()

        // Act
        val read = doc.readMerchants().single()

        // Assert
        assertEquals(MerchantType.Other, read.type)
    }

    @Test
    fun twoDevicesAddingMerchantsMergeWithoutLosingEither() {
        // Arrange
        val phone = Document()
        addMerchant(phone, "m1", Merchant(name = "Phone's shop"))
        val tablet = phone.fork()
        addMerchant(phone, "m2", Merchant(name = "Added on phone"))
        addMerchant(tablet, "m3", Merchant(name = "Added on tablet"))

        // Act
        phone.merge(tablet)

        // Assert
        assertEquals(listOf("Added on phone", "Added on tablet", "Phone's shop"), phone.readMerchants().map { it.name })
    }
}
