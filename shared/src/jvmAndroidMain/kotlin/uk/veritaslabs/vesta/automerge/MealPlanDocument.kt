package uk.veritaslabs.vesta.automerge

import java.util.Date
import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.Read
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.screens.MealGuest
import uk.veritaslabs.vesta.ui.screens.MealPlan
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.vesta.ui.screens.PlannedMeal

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

private const val GuestPrefix = "guest$Separator"
private const val ChosenPrefix = "eaters$Separator"
private const val EaterPrefix = "eat$Separator"
private const val EaterParts = 4

private fun mealKey(date: CalendarDate, slot: MealSlot, recipeKey: String) = mealPlanKey(date, slot, recipeKey)

/**
 * Who eats a planned recipe, as more flat root keys (same reason as [mealPlanKey]):
 *  - `"guest|<id>"`: a person who eats with the Home without being a member; the value is their name.
 *  - `"eaters|<date>|<Slot>|<recipe key>"`: true once someone has chosen that recipe's eaters. Without it the eaters are the default,
 *    every Home member, worked out when shown so a member who joins later is included.
 *  - `"eat|<date>|<Slot>|<recipe key>|<ref>"`: true for each chosen eater, `ref` being `m:<member id>` or `p:<guest id>`.
 * Two devices that choose the eaters of the same recipe at once merge to the union of what they ticked. Taking the recipe out of
 * its slot takes its eaters with it.
 */
internal fun guestKey(id: String) = "$GuestPrefix$id"

internal fun chosenKey(date: CalendarDate, slot: MealSlot, recipeKey: String) = "$ChosenPrefix${mealKey(date, slot, recipeKey)}"

internal fun eaterKey(date: CalendarDate, slot: MealSlot, recipeKey: String, ref: String) = "$EaterPrefix${mealKey(date, slot, recipeKey)}$Separator$ref"

/** Reads the whole plan. A key this version can't make sense of (a future slot, a damaged date) is skipped. */
fun Read.readMealPlan(): MealPlan {
    data class Entry(val date: CalendarDate, val slot: MealSlot, val recipeKey: String, val plannedAt: Long)

    val rootKeys = keys(ObjectId.ROOT).getOrNull().orEmpty()
    val guests = rootKeys.filter { it.startsWith(GuestPrefix) }.mapNotNull { key ->
        val name = (get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        MealGuest(key.removePrefix(GuestPrefix), name)
    }.sortedBy { it.name.lowercase() }
    fun meal(text: String): PlannedMeal? {
        val parts = text.split(Separator, limit = KeyParts)
        if (parts.size != KeyParts) return null
        val date = parseDate(parts[0]) ?: return null
        val slot = MealSlot.entries.firstOrNull { it.name == parts[1] } ?: return null
        return PlannedMeal(date, slot, parts[2])
    }
    val chosen = rootKeys.filter { it.startsWith(ChosenPrefix) }.mapNotNull { meal(it.removePrefix(ChosenPrefix)) }.toSet()
    val eaters = chosen.associateWith { mutableSetOf<String>() }
    rootKeys.filter { it.startsWith(EaterPrefix) }.forEach { key ->
        val parts = key.removePrefix(EaterPrefix).split(Separator, limit = EaterParts)
        if (parts.size != EaterParts) return@forEach
        val meal = meal("${parts[0]}$Separator${parts[1]}$Separator${parts[2]}") ?: return@forEach
        eaters[meal]?.add(parts[3])
    }

    val entries = rootKeys.mapNotNull { key ->
        val parts = key.split(Separator, limit = KeyParts)
        if (parts.size != KeyParts) return@mapNotNull null
        val date = parseDate(parts[0]) ?: return@mapNotNull null
        val slot = MealSlot.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
        val plannedAt = (get(ObjectId.ROOT, key).getOrNull() as? AmValue.Timestamp)?.value?.time ?: 0L
        Entry(date, slot, parts[2], plannedAt)
    }
    return MealPlan(
        days = entries.groupBy { it.date }.mapValues { (_, day) ->
            day.groupBy { it.slot }.mapValues { (_, slot) -> slot.sortedWith(compareBy({ it.plannedAt }, { it.recipeKey })).map { it.recipeKey } }
        },
        guests = guests,
        eaters = eaters,
    )
}

/** Makes exactly [refs] the eaters of [recipeKey] in [slot] on [date]. Returns false, changing nothing, if that is already so. */
fun chooseEaters(doc: Document, date: CalendarDate, slot: MealSlot, recipeKey: String, refs: Set<String>): Boolean {
    val tx = doc.startTransaction()
    var changed = false
    if (!tx.get(ObjectId.ROOT, chosenKey(date, slot, recipeKey)).isPresent) {
        tx.set(ObjectId.ROOT, chosenKey(date, slot, recipeKey), true)
        changed = true
    }
    val prefix = "$EaterPrefix${mealKey(date, slot, recipeKey)}$Separator"
    val existing = tx.keys(ObjectId.ROOT).getOrNull().orEmpty().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    (existing - refs).forEach {
        tx.delete(ObjectId.ROOT, eaterKey(date, slot, recipeKey, it))
        changed = true
    }
    (refs - existing).forEach {
        tx.set(ObjectId.ROOT, eaterKey(date, slot, recipeKey, it), true)
        changed = true
    }
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Adds a guest under [id] called [name]. */
fun addMealGuest(doc: Document, id: String, name: String) {
    val tx = doc.startTransaction()
    tx.set(ObjectId.ROOT, guestKey(id), name)
    tx.commit()
}

/** Renames the guest [id]. Returns false, changing nothing, if there is no such guest or the name is the same. */
fun renameMealGuest(doc: Document, id: String, name: String): Boolean {
    val tx = doc.startTransaction()
    val current = (tx.get(ObjectId.ROOT, guestKey(id)).getOrNull() as? AmValue.Str)?.value
    if (current == null || current == name) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, guestKey(id), name)
    tx.commit()
    return true
}

/** Removes the guest [id]; their ticks are left, and ignored, since no guest has the id. Returns false if there was none. */
fun removeMealGuest(doc: Document, id: String): Boolean {
    val tx = doc.startTransaction()
    if (!tx.get(ObjectId.ROOT, guestKey(id)).isPresent) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, guestKey(id))
    tx.commit()
    return true
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
    // Its eaters go with it, so planning it again later starts from the default.
    val eatersPrefix = "$EaterPrefix${mealKey(date, slot, recipeKey)}$Separator"
    tx.keys(ObjectId.ROOT).getOrNull().orEmpty().filter { it.startsWith(eatersPrefix) }.forEach { tx.delete(ObjectId.ROOT, it) }
    if (tx.get(ObjectId.ROOT, chosenKey(date, slot, recipeKey)).isPresent) tx.delete(ObjectId.ROOT, chosenKey(date, slot, recipeKey))
    tx.commit()
    return true
}
