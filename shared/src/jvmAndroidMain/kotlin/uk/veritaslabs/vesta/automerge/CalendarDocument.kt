package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventColourCount
import uk.veritaslabs.vesta.calendar.EventRepeat

private const val TitleKey = "title"
private const val StartKey = "start"
private const val EndKey = "end"
private const val AllDayKey = "allDay"
private const val RepeatKey = "repeat"
private const val ColorKey = "color"
private const val ParticipantsKey = "participants"
private const val LocationKey = "location"
private const val DescriptionKey = "description"
private const val ParticipantSeparator = '\n'

/*
 * One map at the document root per event, under the event's id (a UUID), like the Home registry (ADR-013): there is no
 * wrapping "events" map for two devices to create at once. Each field is its own key, so two devices editing different
 * fields of one event both win. Times are decimal strings of UTC milliseconds.
 */

/** Every event in the document, tagged as being on [calendar]. An entry this version can't make sense of is skipped. */
fun Read.readCalendarEvents(calendar: EventCalendar): List<CalendarEvent> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        val start = str(id, StartKey)?.toLongOrNull() ?: return@mapNotNull null
        val end = str(id, EndKey)?.toLongOrNull() ?: return@mapNotNull null
        CalendarEvent(
            id = entry.key,
            calendar = calendar,
            title = str(id, TitleKey).orEmpty(),
            allDay = (get(id, AllDayKey).getOrNull() as? AmValue.Bool)?.value == true,
            startMillis = start,
            endMillis = end,
            repeat = EventRepeat.entries.firstOrNull { it.name == str(id, RepeatKey) } ?: EventRepeat.None,
            color = (str(id, ColorKey)?.toIntOrNull() ?: 0).coerceIn(0, EventColourCount - 1),
            participants = str(id, ParticipantsKey).orEmpty().split(ParticipantSeparator).filter { it.isNotEmpty() }.toSet(),
            location = str(id, LocationKey).orEmpty(),
            description = str(id, DescriptionKey).orEmpty(),
        )
    }

/** Adds [event], or brings the stored one with its id up to date, field by field. Returns whether anything changed. */
fun putCalendarEvent(doc: Document, event: CalendarEvent): Boolean {
    val tx = doc.startTransaction()
    val existing = (tx.get(ObjectId.ROOT, event.id).getOrNull() as? AmValue.Map)?.id
    var changed = existing == null
    val entry = existing ?: tx.set(ObjectId.ROOT, event.id, ObjectType.MAP)
    fun put(key: String, value: String) {
        if (tx.str(entry, key) != value) { tx.set(entry, key, value); changed = true }
    }
    put(TitleKey, event.title)
    put(StartKey, event.startMillis.toString())
    put(EndKey, event.endMillis.toString())
    if ((tx.get(entry, AllDayKey).getOrNull() as? AmValue.Bool)?.value != event.allDay) { tx.set(entry, AllDayKey, event.allDay); changed = true }
    put(RepeatKey, event.repeat.name)
    put(ColorKey, event.color.toString())
    put(ParticipantsKey, event.participants.sorted().joinToString(ParticipantSeparator.toString()))
    put(LocationKey, event.location)
    put(DescriptionKey, event.description)
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Removes the event [id]. Returns false, changing nothing, if there was none. */
fun deleteCalendarEvent(doc: Document, id: String): Boolean {
    val tx = doc.startTransaction()
    if ((tx.get(ObjectId.ROOT, id).getOrNull() as? AmValue.Map) == null) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, id)
    tx.commit()
    return true
}

private fun Read.str(obj: ObjectId, key: String): String? = (get(obj, key).getOrNull() as? AmValue.Str)?.value
