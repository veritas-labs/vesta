package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MerchantsLogicTest {

    @Test
    fun timesParseInTheFormsAPersonTypesThem() {
        // Arrange
        val inputs = listOf("09:00", "9:05", " 17:30 ", "0930", "930", "24:00")

        // Act
        val parsed = inputs.map(::parseTime)

        // Assert
        assertEquals(listOf(540, 545, 1050, 570, 570, 1440), parsed)
    }

    @Test
    fun textThatIsNotATimeDoesNotParse() {
        // Arrange
        val inputs = listOf("", "9", "25:00", "24:01", "12:60", "12:5", "ab:cd", "1:2:3", "-1:00")

        // Act
        val parsed = inputs.map(::parseTime)

        // Assert
        assertEquals(inputs.map { null }, parsed)
    }

    @Test
    fun timesFormatWithLeadingZeros() {
        // Arrange / Act
        val formatted = listOf(0, 545, 1050, 1440).map(::formatTime)

        // Assert
        assertEquals(listOf("00:00", "09:05", "17:30", "24:00"), formatted)
    }

    @Test
    fun coordinatesParseWithinTheirLimitsAndAcceptACommaDecimal() {
        // Arrange / Act
        val ok = parseCoordinate(" 53,4808 ", 90.0)
        val edge = parseCoordinate("-180", 180.0)
        val tooBig = parseCoordinate("90.1", 90.0)
        val text = parseCoordinate("north", 90.0)

        // Assert
        assertEquals(53.4808, ok)
        assertEquals(-180.0, edge)
        assertNull(tooBig)
        assertNull(text)
    }

    @Test
    fun degreesRoundTripThroughMicrodegrees() {
        // Arrange / Act
        val micro = degreesToMicrodegrees(-2.2426)
        val back = microdegreesToDegrees(micro)

        // Assert
        assertEquals(-2_242_600, micro)
        assertEquals(-2.2426, back)
    }

    @Test
    fun hoursAreNormalisedByDroppingInvalidRangesSortingAndTrimmingTheNote() {
        // Arrange
        val hours = OpeningHours(
            mapOf(
                Weekday.Monday to listOf(OpenRange(600, 700), OpenRange(100, 200), OpenRange(500, 500)),
                Weekday.Tuesday to listOf(OpenRange(0, 0)),
            ),
            note = "  market days  ",
        )

        // Act
        val normalised = hours.normalised()

        // Assert
        assertEquals(mapOf(Weekday.Monday to listOf(OpenRange(100, 200), OpenRange(600, 700))), normalised.days)
        assertEquals("market days", normalised.note)
    }

    @Test
    fun hoursWithNoRangesAndNoNoteAreEmpty() {
        // Arrange / Act
        val empty = OpeningHours(mapOf(Weekday.Monday to emptyList()))
        val withNote = OpeningHours(note = "Ask")

        // Assert
        assertEquals(true, empty.isEmpty)
        assertEquals(false, withNote.isEmpty)
    }

    @Test
    fun serviceNamesSplitIntoKnownServicesAndCustomTagsWithoutDuplicates() {
        // Arrange
        val names = listOf("Butcher", "deli", "Wine", "wine", "  ", "Butcher", "Game")

        // Act
        val (known, custom) = splitServices(names)

        // Assert
        assertEquals(listOf(MerchantService.Butcher, MerchantService.Deli), known)
        assertEquals(listOf("Wine", "Game"), custom)
    }

    private val butcher = Merchant(
        key = "b", name = "Hargreaves", type = MerchantType.IndependentTrader, services = listOf(MerchantService.Butcher),
        customServices = listOf("Game"), address = "12 Market Street",
    )
    private val tesco = Merchant(key = "t", name = "Tesco", type = MerchantType.NationalChain, services = listOf(MerchantService.Groceries))

    @Test
    fun theFilterMatchesNameAddressAndServicesIgnoringCase() {
        // Arrange
        val all = listOf(butcher, tesco)

        // Act
        val byName = filterMerchants(all, MerchantsFilter(query = "tes")).map { it.key }
        val byAddress = filterMerchants(all, MerchantsFilter(query = "MARKET street")).map { it.key }
        val byService = filterMerchants(all, MerchantsFilter(query = "butch")).map { it.key }
        val byCustom = filterMerchants(all, MerchantsFilter(query = "game")).map { it.key }

        // Assert
        assertEquals(listOf("t"), byName)
        assertEquals(listOf("b"), byAddress)
        assertEquals(listOf("b"), byService)
        assertEquals(listOf("b"), byCustom)
    }

    @Test
    fun theFilterNarrowsByTypeAndTheEmptyFilterShowsEverything() {
        // Arrange
        val all = listOf(butcher, tesco)

        // Act
        val chains = filterMerchants(all, MerchantsFilter(type = MerchantType.NationalChain)).map { it.key }
        val everything = filterMerchants(all, MerchantsFilter())

        // Assert
        assertEquals(listOf("t"), chains)
        assertEquals(all, everything)
        assertEquals(false, MerchantsFilter().isActive)
        assertEquals(true, MerchantsFilter(query = "x").isActive)
    }
}
