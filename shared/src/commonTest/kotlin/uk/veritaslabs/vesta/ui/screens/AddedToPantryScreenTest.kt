package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AddedToPantryScreenTest {

    @Test
    fun themedLottie_replacesEachQuotedPlaceholderWithItsColourArray() {
        // Arrange
        val template = """{"a":"__BADGE__","b":"__BADGE__","c":"__PRIMARY__"}"""
        val palette = mapOf("BADGE" to Color(1f, 0f, 1f), "PRIMARY" to Color(0f, 1f, 0f))

        // Act
        val json = themedLottie(template, palette)

        // Assert
        assertEquals("""{"a":[1.0,0.0,1.0,1],"b":[1.0,0.0,1.0,1],"c":[0.0,1.0,0.0,1]}""", json)
    }

    @Test
    fun themedLottie_leavesNoPlaceholderInTheBundledAnimationWhenTheWholePaletteIsGiven() {
        // Arrange
        val names = listOf(
            "BOX_FRONT", "BOX_BACK", "BOX_LABEL", "ITEM_A", "ITEM_B", "ITEM_C", "ITEM_BAND", "BADGE", "ON_BADGE", "PRIMARY", "TERTIARY",
        )
        val template = names.joinToString(",", "[", "]") { "\"__${it}__\"" }

        // Act
        val json = themedLottie(template, names.associateWith { Color.Black })

        // Assert
        assertFalse("__" in json)
    }
}
