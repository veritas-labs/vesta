package uk.veritaslabs.vesta.automerge

import java.util.Date
import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.Read
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.screens.MealPlan
import uk.veritaslabs.vesta.ui.screens.MealSlot

private const val Separator = '|'
private const val KeyParts = 3

/**
 * The root key of one planned recipe: `"2026-10-13|Dinner|<recipe key>"`. Its value is when it was planned (a timestamp),
 * which orders the recipes of a slot. One flat key per entry, rather than a map of days holding maps of slots, because two
 * devices planning the same new day at once would each create the day's map and one would be hidden on merge (ADR-010's
 * race); with flat keys they just add two keys. Planning the same recipe twice is the same key.
 */
internal fun mealPlanKey(date: CalendarDate, slot: MealSlot, recipeKey: String): String =
    "${dateKey(date)}$Separator${slot.name}$Separator$recipeKey"

private fun dateKey(date: CalendarDate): String =
    "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

private fun parseDate(text: String): CalendarDate? {
    val parts = text.split('-')
    if (parts.size != 3) return null
    val (year, month, day) = parts.map { it.toIntOrNull() ?: return null }
    return runCatching { CalendarDate(year, month, day) }.getOrNull()
}

/** Reads the whole plan. A key this version can't make sense of (a future slot, a damaged date) is skipped. */
fun Read.readMealPlan(): MealPlan {
    data class Entry(val date: CalendarDate, val slot: MealSlot, val recipeKey: String, val plannedAt: Long)

    val entries = keys(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { key ->
        val parts = key.split(Separator, limit = KeyParts)
        if (parts.size != KeyParts) return@mapNotNull null
        val date = parseDate(parts[0]) ?: return@mapNotNull null
        val slot = MealSlot.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
        val plannedAt = (get(ObjectId.ROOT, key).getOrNull() as? AmValue.Timestamp)?.value?.time ?: 0L
        Entry(date, slot, parts[2], plannedAt)
    }
    return MealPlan(
        entries.groupBy { it.date }.mapValues { (_, day) ->
            day.groupBy { it.slot }.mapValues { (_, slot) -> slot.sortedWith(compareBy({ it.plannedAt }, { it.recipeKey })).map { it.recipeKey } }
        },
    )
}

/** Plans [recipeKey] for [slot] on [date], stamped [plannedAt]. Returns false, changing nothing, if it was already planned there. */
fun planMeal(doc: Document, date: CalendarDate, slot: MealSlot, recipeKey: String, plannedAt: Long): Boolean {
    val key = mealPlanKey(date, slot, recipeKey)
    val tx = doc.startTransaction()
    if (tx.get(ObjectId.ROOT, key).isPresent) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, Date(plannedAt))
    tx.commit()
    return true
}

/** Takes [recipeKey] out of [slot] on [date]. Returns false, changing nothing, if it wasn't planned there. */
fun unplanMeal(doc: Document, date: CalendarDate, slot: MealSlot, recipeKey: String): Boolean {
    val key = mealPlanKey(date, slot, recipeKey)
    val tx = doc.startTransaction()
    if (!tx.get(ObjectId.ROOT, key).isPresent) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, key)
    tx.commit()
    return true
}
