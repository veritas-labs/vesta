package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.graphics.Color
import uk.veritaslabs.vesta.calendar.EventColourCount

/** The colours an event can have, in the order of `event_colours` in the strings; [uk.veritaslabs.vesta.calendar.CalendarEvent.color] is an index into it. */
internal val EventPalette: List<Color> = listOf(
    Color(0xFF4C6EF5), // blue
    Color(0xFF2F9E44), // green
    Color(0xFFE8A317), // gold
    Color(0xFFE03131), // red
    Color(0xFF9C36B5), // purple
    Color(0xFF0C8599), // teal
    Color(0xFFD6336C), // pink
    Color(0xFF868E96), // grey
).also { check(it.size == EventColourCount) }

internal fun eventColour(index: Int): Color = EventPalette[index.coerceIn(0, EventColourCount - 1)]
