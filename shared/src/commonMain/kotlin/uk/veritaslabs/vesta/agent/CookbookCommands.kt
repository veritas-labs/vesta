package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType

/** "List the recipes in the cookbook of Home [homeId]" — id, name and a summary of each. Read-only; needs Cookbook Read. */
data class ReadCookbook(override val commandId: String, val homeId: String) : AgentCommand

/** "Show me recipe [recipeKey] of Home [homeId] in full" (ingredients and steps too). Read-only; needs Cookbook Read. */
data class ReadRecipe(override val commandId: String, val homeId: String, val recipeKey: String) : AgentCommand

/** "Add [recipe] to the cookbook of Home [homeId]". [recipe] has a blank key; the app chooses one. Needs Cookbook Write. */
data class AddRecipe(override val commandId: String, val homeId: String, val recipe: Recipe) : AgentCommand

/** "Remove a recipe from the cookbook of Home [homeId]", named by its [recipeKey] if given, else by [title]. Needs Cookbook Write. */
data class RemoveRecipe(override val commandId: String, val homeId: String, val recipeKey: String?, val title: String?) : AgentCommand

/**
 * Decodes the cookbook half of the agent vocabulary (ADR-027): `Read` of a cookbook or one recipe, `Add` and `Remove` of a
 * `schema:Recipe`. [parse] returns null when the message is not about a cookbook, so [AgentCommandParser] carries on.
 *
 * Addresses are `urn:vesta:document:<homeId>:cookbook` (the collection) and `urn:vesta:document:<homeId>:cookbook:<recipeKey>`.
 */
object CookbookCommandParser {

    const val MAX_RECIPES = 200
    const val MAX_TITLE = 200
    const val MAX_TEXT = 2_000
    const val MAX_INGREDIENTS = 100
    const val MAX_STEPS = 100
    const val MAX_TAGS = 20
    const val MAX_SERVINGS = 999
    const val MAX_MINUTES = 7 * 24 * 60

    private val Address = Regex("^urn:vesta:document:([^:]+):cookbook(?::([^:]+))?$")
    private val Duration = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?$")

    /** The cookbook collection address of [homeId]. */
    fun collectionId(homeId: String) = "urn:vesta:document:$homeId:cookbook"

    fun parse(root: JsonObject, commandId: String): ParseResult? {
        val type = root.string("type")
        return when (type) {
            "Read" -> {
                val match = Address.matchEntire((root["object"] as? JsonObject)?.string("id") ?: return null) ?: return null
                val (homeId, key) = match.destructured
                ParseResult.Command(if (key.isEmpty()) ReadCookbook(commandId, homeId) else ReadRecipe(commandId, homeId, key))
            }
            "Add", "Remove" -> {
                val match = Address.matchEntire((root["target"] as? JsonObject)?.string("id") ?: return null) ?: return null
                if (match.groupValues[2].isNotEmpty()) return null
                val homeId = match.groupValues[1]
                val node = root["object"] as? JsonObject
                if (node?.string("type") != "schema:Recipe") return ParseResult.Invalid(commandId, "object must be a schema:Recipe")
                if (type == "Add") parseAdd(node, homeId, commandId) else parseRemove(node, homeId, commandId)
            }
            else -> null
        }
    }

    private fun parseRemove(node: JsonObject, homeId: String, commandId: String): ParseResult {
        val key = node.string("schema:identifier")?.trim()?.takeIf { it.isNotEmpty() }
        val title = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() }
        if (key == null && title == null) return ParseResult.Invalid(commandId, "schema:identifier or schema:name is required")
        return ParseResult.Command(RemoveRecipe(commandId, homeId, key, title))
    }

    private fun parseAdd(node: JsonObject, homeId: String, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)

        val title = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")
        if (title.length > MAX_TITLE) return invalid("title too long")

        val description = node.optionalText("schema:description") ?: return invalid("description must be a string of at most $MAX_TEXT characters")
        val source = node.optionalText("schema:isBasedOn") ?: return invalid("isBasedOn must be a string of at most $MAX_TEXT characters")

        val type = when (val category = node["schema:recipeCategory"]) {
            null -> RecipeType.Dinner
            is JsonPrimitive -> category.stringOrNull()?.let { name -> RecipeType.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: RecipeType.Other }
                ?: return invalid("recipeCategory must be a string")
            else -> return invalid("recipeCategory must be a string")
        }

        val servings = when (val yield = node["schema:recipeYield"]) {
            null -> 4
            is JsonPrimitive -> yield.takeUnless { it.isString }?.intOrNull ?: return invalid("recipeYield must be an integer")
            else -> return invalid("recipeYield must be an integer")
        }
        if (servings !in 1..MAX_SERVINGS) return invalid("recipeYield out of range")

        val prep = node.minutes("schema:prepTime") ?: return invalid("prepTime must be an ISO 8601 duration such as PT15M, at most 7 days")
        val cook = node.minutes("schema:cookTime") ?: return invalid("cookTime must be an ISO 8601 duration such as PT1H30M, at most 7 days")

        val ingredients = when (val raw = node["schema:recipeIngredient"]) {
            null -> emptyList()
            is JsonArray -> raw.map { parseIngredient(it) ?: return invalid("each recipeIngredient must be a string or an object with a schema:name") }
            else -> return invalid("recipeIngredient must be an array")
        }
        if (ingredients.size > MAX_INGREDIENTS) return invalid("too many ingredients")

        val steps = when (val raw = node["schema:recipeInstructions"]) {
            null -> emptyList()
            is JsonArray -> raw.mapIndexed { index, step -> parseStep(step, index) ?: return invalid("each recipeInstructions entry must be a string or an object with a schema:text") }
            else -> return invalid("recipeInstructions must be an array")
        }
        if (steps.size > MAX_STEPS) return invalid("too many steps")

        val tags = when (val raw = node["schema:keywords"]) {
            null -> emptyList()
            is JsonPrimitive -> listOf(raw.stringOrNull() ?: return invalid("keywords must be a string or an array of strings")).flatMap { it.split(',') }
            is JsonArray -> raw.map { it.stringOrNull() ?: return invalid("keywords must be a string or an array of strings") }
            else -> return invalid("keywords must be a string or an array of strings")
        }.map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.size > MAX_TAGS) return invalid("too many keywords")

        return ParseResult.Command(
            AddRecipe(
                commandId, homeId,
                Recipe(
                    title = title, description = description, type = type, servings = servings,
                    prepMinutes = prep, cookMinutes = cook, ingredients = ingredients, steps = steps, tags = tags, source = source,
                ),
            ),
        )
    }

    private fun parseIngredient(element: JsonElement): RecipeIngredient? {
        if (element is JsonPrimitive) return element.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE }?.let { RecipeIngredient(it) }
        val node = element as? JsonObject ?: return null
        val name = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE } ?: return null
        val quantity = when (val value = node["schema:value"]) {
            null -> null
            is JsonPrimitive -> value.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return null
            else -> return null
        }
        val unit = node.optionalText("schema:unitText", MAX_TITLE) ?: return null
        val note = node.optionalText("schema:description", MAX_TITLE) ?: return null
        return RecipeIngredient(name, quantity, unit, note)
    }

    private fun parseStep(element: JsonElement, index: Int): RecipeStep? {
        val id = "step-${index + 1}"
        if (element is JsonPrimitive) return element.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }?.let { RecipeStep(id, it) }
        val node = element as? JsonObject ?: return null
        val text = node.string("schema:text")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT } ?: return null
        val minutes = node.minutes("schema:performTime") ?: return null
        return RecipeStep(id, text, minutes.takeIf { node["schema:performTime"] != null })
    }

    /** An ISO 8601 `PT#H#M` duration (or whole minutes) as minutes: 0 when absent, null when malformed or over [MAX_MINUTES]. */
    private fun JsonObject.minutes(key: String): Int? {
        val element = this[key] ?: return 0
        val primitive = element as? JsonPrimitive ?: return null
        val minutes = if (primitive.isString) {
            val match = Duration.matchEntire(primitive.content.trim()) ?: return null
            if (match.groupValues[1].isEmpty() && match.groupValues[2].isEmpty()) return null
            (match.groupValues[1].toIntOrNull() ?: 0).toLong() * 60 + (match.groupValues[2].toIntOrNull() ?: 0)
        } else {
            primitive.intOrNull?.toLong() ?: return null
        }
        return minutes.takeIf { it in 0..MAX_MINUTES }?.toInt()
    }

    /** The string at [key] trimmed ("" when absent), or null when present but not a string or longer than [max]. */
    private fun JsonObject.optionalText(key: String, max: Int = MAX_TEXT): String? {
        val element = this[key] ?: return ""
        return element.stringOrNull()?.trim()?.takeIf { it.length <= max }
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()
}

/** ADR-027's cookbook replies: AS2 `Accept` carrying `schema:Recipe`s. */
object CookbookReplies {

    /** Answer to [ReadCookbook]: a `Collection` of summaries; [totalItems] may exceed `recipes.size` (see [CookbookCommandParser.MAX_RECIPES]). */
    fun cookbook(replyId: String, commandId: String, homeId: String, recipes: List<Recipe>, totalItems: Int): String =
        accept(replyId, commandId) {
            put("type", "Collection")
            put("id", CookbookCommandParser.collectionId(homeId))
            put("totalItems", JsonPrimitive(totalItems))
            put("items", buildJsonArray { recipes.forEach { add(summary(it)) } })
        }

    /** Answer to [ReadRecipe]: the whole recipe. Photos and videos are left out: they are references that stay on the devices. */
    fun recipe(replyId: String, commandId: String, recipe: Recipe): String =
        accept(replyId, commandId) {
            summary(recipe).forEach { (key, value) -> put(key, value) }
            put("schema:prepTime", duration(recipe.prepMinutes))
            put("schema:cookTime", duration(recipe.cookMinutes))
            put(
                "schema:recipeIngredient",
                buildJsonArray {
                    recipe.ingredients.forEach { ingredient ->
                        add(
                            buildJsonObject {
                                put("type", "schema:QuantitativeValue")
                                put("schema:name", ingredient.name)
                                ingredient.quantity?.let { put("schema:value", JsonPrimitive(it)) }
                                if (ingredient.unit.isNotBlank()) put("schema:unitText", ingredient.unit)
                                if (ingredient.note.isNotBlank()) put("schema:description", ingredient.note)
                            },
                        )
                    }
                },
            )
            put(
                "schema:recipeInstructions",
                buildJsonArray {
                    recipe.steps.forEach { step ->
                        add(
                            buildJsonObject {
                                put("type", "schema:HowToStep")
                                put("schema:text", step.text)
                                step.minutes?.let { put("schema:performTime", duration(it)) }
                            },
                        )
                    }
                },
            )
            if (recipe.source.isNotBlank()) put("schema:isBasedOn", recipe.source)
        }

    /** Answer to a successful [AddRecipe] or [RemoveRecipe]: the recipe's id and name only. */
    fun done(replyId: String, commandId: String, key: String, title: String): String =
        accept(replyId, commandId) {
            put("type", "schema:Recipe")
            put("id", key)
            put("schema:identifier", key)
            put("schema:name", title)
        }

    private fun accept(replyId: String, commandId: String, result: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("result", buildJsonObject(result))
        }.toString()

    private fun summary(recipe: Recipe): JsonObject = buildJsonObject {
        put("type", "schema:Recipe")
        put("id", recipe.key)
        put("schema:identifier", recipe.key)
        put("schema:name", recipe.title)
        if (recipe.description.isNotBlank()) put("schema:description", recipe.description)
        put("schema:recipeCategory", recipe.type.name)
        put("schema:recipeYield", JsonPrimitive(recipe.servings))
        put("schema:totalTime", duration(recipe.totalMinutes))
        if (recipe.tags.isNotEmpty()) put("schema:keywords", buildJsonArray { recipe.tags.forEach { add(JsonPrimitive(it)) } })
    }

    private fun duration(minutes: Int): String = "PT${minutes / 60}H${minutes % 60}M".replace("PT0H", "PT")
}
