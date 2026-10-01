package uk.veritaslabs.vesta.pantry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackageSizeTest {

    @Test
    fun formatAttachesMetricUnitsAndSpacesTheRest() {
        // Arrange
        val metric = PackageSize("500", PackageUnits.first { it.symbol == "ml" }, null)
        val imperial = PackageSize("2", PackageUnits.first { it.symbol == "pint" }, null)
        val both = PackageSize("15", PackageUnits.first { it.symbol == "oz" }, PackageContainers.first { it.name == "tin" })
        val countOnly = PackageSize("6", null, PackageContainers.first { it.name == "pack" })

        // Act
        val formatted = listOf(metric, imperial, both, countOnly).map { it.format() }

        // Assert
        assertEquals(listOf("500ml", "2 pint", "15 oz tin", "6 pack"), formatted)
    }

    @Test
    fun parseNormalisesAliasesCasePluralsAndSpacing() {
        // Arrange
        val inputs = listOf("500 ML", "1.5 Litres", "15 ounce can", "2 Pints", "6 packs", "400g tin", "12 fl. oz bottle", "1,5kg bag")

        // Act
        val normalised = inputs.map { PackageSize.parse(it)?.format() }

        // Assert
        assertEquals(
            listOf("500ml", "1.5l", "15 oz tin", "2 pint", "6 pack", "400g tin", "12 fl oz bottle", "1.5kg bag"),
            normalised,
        )
    }

    @Test
    fun parseRejectsTextItDoesNotFullyUnderstand() {
        // Arrange
        val inputs = listOf("", "tin", "500", "large", "500ml fizzy", "a few jars")

        // Act
        val parsed = inputs.map { PackageSize.parse(it) }

        // Assert
        parsed.forEach { assertNull(it) }
    }

    @Test
    fun normaliseLeavesUnrecognisedTextTrimmedAndTidiesRecognisedText() {
        // Arrange
        val recognised = " 1 Bottle "
        val unrecognised = "  family size "

        // Act
        val a = PackageSize.normalise(recognised)
        val b = PackageSize.normalise(unrecognised)

        // Assert
        assertEquals("1 bottle", a)
        assertEquals("family size", b)
    }

    @Test
    fun formatThenParseRoundTripsEveryUnitAndContainer() {
        // Arrange
        val sizes = PackageUnits.flatMap { u -> PackageContainers.map { c -> PackageSize("3", u, c) } } +
            PackageUnits.map { PackageSize("3", it, null) } +
            PackageContainers.map { PackageSize("3", null, it) }

        // Act
        val roundTripped = sizes.map { PackageSize.parse(it.format()) }

        // Assert
        assertEquals(sizes, roundTripped)
    }
}
