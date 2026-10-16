package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.screens.MealSlot

/** "Show me the meal plan of Home [homeId]", optionally only the days from [from] to [to] inclusive. Read-only; needs Meal planner Read. */
data class ReadMealPlan(
    override val commandId: String,
    val homeId: String,
    val from: CalendarDate?,
    val to: CalendarDate?,
) : AgentCommand

/** A command that changes a Home's meal plan; needs the Meal planner Write capability. */
sealed interface MealPlanWriteCommand : AgentCommand {
    val homeId: String
    val date: CalendarDate
    val slot: MealSlot
    val recipeKey: String

    /** One line for the activity list and the Allow once card, e.g. "Plan a recipe for Dinner on 2026-10-13". */
    val summary: String
}

/** "Plan the cookbook recipe [recipeKey] for [slot] on [date]"; planning it where it already is changes nothing. */
data class PlanMeal(
    override val commandId: String,
    override val homeId: String,
    override val date: CalendarDate,
    override val slot: MealSlot,
    override val recipeKey: String,
) : MealPlanWriteCommand {
    override val summary get() = "Plan a recipe for ${slot.name} on ${MealPlanCommandParser.dateText(date)}"
}

/** "Take the recipe [recipeKey] out of [slot] on [date]"; it must be planned there. */
data class UnplanMeal(
    override val commandId: String,
    override val homeId: String,
    override val date: CalendarDate,
    override val slot: MealSlot,
    override val recipeKey: String,
) : MealPlanWriteCommand {
    override val summary get() = "Take a recipe out of ${slot.name} on ${MealPlanCommandParser.dateText(date)}"
}

/** One planned recipe as an agent sees it. [recipeKey] is the cookbook's key for the recipe (the plan holds nothing else). */
data class PlannedMealRef(val date: CalendarDate, val slot: MealSlot, val recipeKey: String)

/**
 * Parses the meal planner commands and builds their replies. [AgentCommandParser] hands over any message whose target (or,
 * for a read, object) is a `urn:vesta:document:<homeId>:mealplan`. `Add` plans a recipe, `Remove` takes it out.
 */
object MealPlanCommandParser {

    /** The most planned recipes one [ReadMealPlan] reply carries; `totalItems` still says how many matched. */
    const val MAX_ITEMS = 200

    private const val PREFIX = "urn:vesta:document:"
    private const val SUFFIX = ":mealplan"
    private const val MAX_KEY_LENGTH = 200

    fun documentId(homeId: String) = "$PREFIX$homeId$SUFFIX"

    /** The Home of a meal plan document id, or null if [documentId] is not one. */
    fun homeId(documentId: String?): String? =
        documentId?.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)?.takeIf { it.isNotBlank() }

    /** `2026-10-13` */
    fun dateText(date: CalendarDate) =
        "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

    /** The date of a `yyyy-mm-dd` text, or null if it is not a real day. */
    fun parseDate(text: String): CalendarDate? {
        val parts = text.split('-')
        if (parts.size != 3 || parts[0].length != 4 || parts[1].length != 2 || parts[2].length != 2) return null
        val (year, month, day) = parts.map { it.toIntOrNull() ?: return null }
        return runCatching { CalendarDate(year, month, day) }.getOrNull()
    }

    /** A read of the plan; [objectNode] is the request's `object`, which may carry `schema:startDate` / `schema:endDate`. */
    fun parseRead(objectNode: JsonObject, commandId: String, homeId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        fun date(key: String): CalendarDate? = objectNode[key]?.text()?.let(::parseDate)
        val from = date("schema:startDate")
        val to = date("schema:endDate")
        if (objectNode.containsKey("schema:startDate") && from == null) return invalid("schema:startDate must be a yyyy-mm-dd date")
        if (objectNode.containsKey("schema:endDate") && to == null) return invalid("schema:endDate must be a yyyy-mm-dd date")
        if (from != null && to != null && to < from) return invalid("schema:endDate must not be before schema:startDate")
        return ParseResult.Command(ReadMealPlan(commandId, homeId, from, to))
    }

    /**
     * [type] is `Add` or `Remove`; [homeId] came from the target. The `object` names the meal:
     * `{"type": "Object", "schema:startDate": "2026-10-13", "slot": "Dinner", "schema:recipe": {"type": "schema:Recipe", "id": "<recipe key>"}}`.
     */
    fun parseWrite(root: JsonObject, commandId: String, type: String, homeId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        if (type != "Add" && type != "Remove") return invalid("a meal plan takes Add or Remove")

        val node = root["object"] as? JsonObject ?: return invalid("object is required")
        val date = node["schema:startDate"]?.text()?.let(::parseDate) ?: return invalid("schema:startDate must be a yyyy-mm-dd date")
        val slotName = node["slot"]?.text() ?: return invalid("slot is required")
        val slot = MealSlot.entries.firstOrNull { it.name.equals(slotName.trim(), ignoreCase = true) }
            ?: return invalid("slot must be one of ${MealSlot.entries.joinToString(", ") { it.name }}")
        val recipe = node["schema:recipe"] as? JsonObject ?: return invalid("schema:recipe is required")
        if (recipe.text("type") != "schema:Recipe") return invalid("schema:recipe must be a schema:Recipe")
        val key = recipe.text("id")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("schema:recipe.id is required")
        // The key is written into a document key, where `|` separates the parts.
        if ('|' in key || key.length > MAX_KEY_LENGTH) return invalid("schema:recipe.id is not a recipe key")

        return ParseResult.Command(
            if (type == "Add") PlanMeal(commandId, homeId, date, slot, key) else UnplanMeal(commandId, homeId, date, slot, key),
        )
    }

    /** Answer to [ReadMealPlan]: an `Accept` whose `result` is a `Collection` of planned meals, earliest day first. */
    fun readReply(replyId: String, commandId: String, homeId: String, meals: List<PlannedMealRef>, totalItems: Int): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put(
                "result",
                buildJsonObject {
                    put("type", "Collection")
                    put("id", documentId(homeId))
                    put("totalItems", JsonPrimitive(totalItems))
                    put("items", buildJsonArray { meals.forEach { add(meal(it)) } })
                },
            )
        }.toString()

    /** Answer to a write: an `Accept` whose `result` is the meal that was planned (or, for [UnplanMeal], taken out). */
    fun writeReply(replyId: String, commandId: String, meal: PlannedMealRef): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("result", meal(meal))
        }.toString()

    private fun meal(meal: PlannedMealRef) = buildJsonObject {
        put("type", "Object")
        put("id", "urn:vesta:meal:${dateText(meal.date)}:${meal.slot.name}:${meal.recipeKey}")
        put("schema:startDate", dateText(meal.date))
        put("slot", meal.slot.name)
        put("schema:recipe", buildJsonObject { put("type", "schema:Recipe"); put("id", meal.recipeKey) })
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.text(key: String): String? = this[key]?.text()
}
