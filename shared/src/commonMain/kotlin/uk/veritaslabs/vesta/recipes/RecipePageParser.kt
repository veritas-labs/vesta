package uk.veritaslabs.vesta.recipes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType

private const val MaxImportedTags = 8

private val LdJsonScript = Regex(
    """<script[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>([\s\S]*?)</script>""",
    RegexOption.IGNORE_CASE,
)

/**
 * The recipe on the web page [html] (downloaded from [pageUrl]), read from the schema.org `Recipe` data that nearly every recipe site embeds
 * as JSON-LD; null if the page has none or it has no title. Sections of steps are flattened into the one list.
 */
fun parseRecipePage(html: String, pageUrl: String): RecipeImportResult.Imported? {
    val data = LdJsonScript.findAll(html).mapNotNull { match ->
        val body = match.groupValues[1].trim().removePrefix("<![CDATA[").removeSuffix("]]>").trim()
        runCatching { Json.parseToJsonElement(body) }.getOrNull()?.let(::findRecipe)
    }.firstOrNull() ?: return null
    val title = data.text("name").orEmpty()
    if (title.isBlank()) return null

    val prep = data.minutes("prepTime")
    val total = data.minutes("totalTime")
    // A page that only gives a total has no way to say how it splits, so it is all cooking time.
    val cook = data.minutes("cookTime").takeIf { it > 0 || prep > 0 } ?: total
    val texts = instructionTexts(data["recipeInstructions"])
    val recipe = Recipe(
        title = title,
        description = data.text("description").orEmpty(),
        type = recipeTypeOf(strings(data["recipeCategory"])),
        servings = strings(data["recipeYield"]).firstNotNullOfOrNull { firstNumber(it) }?.takeIf { it > 0 } ?: Recipe().servings,
        prepMinutes = prep,
        cookMinutes = cook,
        restMinutes = (total - prep - cook).coerceAtLeast(0),
        ingredients = strings(data["recipeIngredient"]).map(::cleanText).filter { it.isNotBlank() }.map(::parseIngredientLine),
        steps = texts.mapIndexed { index, text -> RecipeStep("s$index", text) },
        tags = keywords(data["keywords"]),
        source = pageUrl,
    )
    return RecipeImportResult.Imported(recipe, imageLink(data["image"])?.let { resolveLink(pageUrl, it) })
}

private fun findRecipe(element: JsonElement): JsonObject? = when (element) {
    is JsonArray -> element.firstNotNullOfOrNull(::findRecipe)
    is JsonObject -> element.takeIf(::isRecipe) ?: listOf("@graph", "mainEntity").firstNotNullOfOrNull { key -> element[key]?.let(::findRecipe) }
    else -> null
}

private fun isRecipe(node: JsonObject): Boolean = strings(node["@type"]).any { it.endsWith("Recipe") }

/** Every string in [element], whether it is one string, a number, a list of them, or an object (then its `text` or `name`). */
private fun strings(element: JsonElement?): List<String> = when (element) {
    null -> emptyList()
    is JsonPrimitive -> listOf(element.content)
    is JsonArray -> element.flatMap { strings(it) }
    is JsonObject -> strings(element["text"] ?: element["name"])
}

private fun JsonObject.text(key: String): String? = strings(this[key]).firstOrNull()?.let(::cleanText)

private fun JsonObject.minutes(key: String): Int = strings(this[key]).firstOrNull()?.let(::durationMinutes) ?: 0

/** The steps in [element], in order: a string (a line each), a list, `HowToStep`s, or `HowToSection`s of steps. */
private fun instructionTexts(element: JsonElement?): List<String> = when (element) {
    null -> emptyList()
    is JsonPrimitive -> htmlLines(element.content)
    is JsonArray -> element.flatMap { instructionTexts(it) }
    is JsonObject -> element["itemListElement"]?.let { instructionTexts(it) } ?: instructionTexts(element["text"] ?: element["name"])
}

private fun keywords(element: JsonElement?): List<String> =
    strings(element).flatMap { it.split(',') }.map(::cleanText).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MaxImportedTags)

private fun imageLink(element: JsonElement?): String? = when (element) {
    null -> null
    is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
    is JsonArray -> element.firstNotNullOfOrNull { imageLink(it) }
    is JsonObject -> imageLink(element["url"] ?: element["contentUrl"])
}

private fun recipeTypeOf(categories: List<String>): RecipeType {
    val text = categories.joinToString(" ").lowercase()
    return when {
        text.isBlank() -> RecipeType.Other
        "breakfast" in text || "brunch" in text -> RecipeType.Breakfast
        "lunch" in text -> RecipeType.Lunch
        "dessert" in text || "pudding" in text -> RecipeType.Dessert
        "baking" in text || "bread" in text || "cake" in text -> RecipeType.Baking
        "starter" in text || "appetizer" in text || "appetiser" in text || "soup" in text -> RecipeType.Starter
        "side" in text -> RecipeType.Side
        "snack" in text -> RecipeType.Snack
        "drink" in text || "beverage" in text || "cocktail" in text -> RecipeType.Drink
        "dinner" in text || "main" in text || "supper" in text -> RecipeType.Dinner
        else -> RecipeType.Other
    }
}

private fun firstNumber(text: String): Int? = Regex("""\d+""").find(text)?.value?.toIntOrNull()

private val Duration = Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:\d+S)?)?$""", RegexOption.IGNORE_CASE)

/** Whole minutes in an ISO-8601 duration such as `PT1H30M`; null if [text] isn't one. */
internal fun durationMinutes(text: String): Int? {
    val match = Duration.matchEntire(text.trim()) ?: return null
    val (days, hours, minutes) = match.destructured
    return (days.toIntOrNull() ?: 0) * 24 * 60 + (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
}

private fun resolveLink(pageUrl: String, link: String): String {
    val origin = Regex("""^https?://[^/?#]+""").find(pageUrl)?.value.orEmpty()
    return when {
        link.startsWith("http://") || link.startsWith("https://") -> link
        link.startsWith("//") -> pageUrl.substringBefore("//") + link
        link.startsWith("/") -> origin + link
        else -> pageUrl.substringBefore('?').substringBeforeLast('/') + "/" + link
    }
}

private val Entities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "deg" to "°", "frac12" to "½", "frac14" to "¼", "frac34" to "¾")

private fun decodeEntities(text: String): String =
    Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z0-9]+);""").replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") -> name.drop(2).toIntOrNull(16)?.let { codePointString(it) }
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { codePointString(it) }
            else -> Entities[name]
        } ?: match.value
    }

private fun codePointString(codePoint: Int): String? =
    if (codePoint in 1..0xFFFF) codePoint.toChar().toString() else null

/** [html] as plain text on one line: tags dropped, entities decoded, runs of white space collapsed. */
internal fun cleanText(html: String): String =
    decodeEntities(html.replace(Regex("<[^>]*>"), " ")).replace(Regex("""\s+"""), " ").trim()

/** [html] as plain text, one entry per line (a line break, paragraph or list item ends a line); blank lines are dropped. */
private fun htmlLines(html: String): List<String> =
    html.replace(Regex("""<\s*(br\s*/?|/p|/li|/div)\s*>""", RegexOption.IGNORE_CASE), "\n").split('\n').map(::cleanText).filter { it.isNotEmpty() }

private val Fractions = mapOf(
    '½' to "1/2", '¼' to "1/4", '¾' to "3/4", '⅓' to "1/3", '⅔' to "2/3", '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
)

private val IngredientQuantity = Regex("""^(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)(?:\s*(?:-|–|to)\s*(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?))?\s*""")

private val IngredientUnits = setOf(
    "cup", "cups", "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons", "g", "gram", "grams", "kg", "kilogram", "kilograms",
    "ml", "millilitre", "millilitres", "milliliter", "milliliters", "l", "litre", "litres", "liter", "liters", "oz", "ounce", "ounces",
    "lb", "lbs", "pound", "pounds", "clove", "cloves", "pinch", "pinches", "can", "cans", "tin", "tins", "slice", "slices", "bunch", "bunches",
    "pack", "packs", "packet", "packets", "stick", "sticks", "sprig", "sprigs", "handful", "handfuls", "dash", "dashes",
)

private fun quantityOf(text: String): Double? {
    val number = text.replace(',', '.').trim()
    val parts = number.split(Regex("""\s+"""))
    fun fraction(part: String): Double? = part.split('/').let { if (it.size == 2) it[0].toDoubleOrNull()?.div(it[1].toDoubleOrNull() ?: return null) else part.toDoubleOrNull() }
    return parts.map { fraction(it) ?: return null }.sum()
}

/**
 * One ingredient line as sites write it ("1 1/2 cups plain flour, sifted") split into quantity, unit, name and note. A line with no leading number
 * ("salt to taste") keeps the whole line as the name; a range ("2-3 eggs") takes its first number.
 */
internal fun parseIngredientLine(line: String): RecipeIngredient {
    val text = Fractions.entries.fold(line.trim()) { acc, (symbol, fraction) -> acc.replace("$symbol", " $fraction") }.replace(Regex("""\s+"""), " ").trim()
    val match = IngredientQuantity.find(text) ?: return RecipeIngredient(name = line.trim())
    val quantity = quantityOf(match.groupValues[1]) ?: return RecipeIngredient(name = line.trim())
    var rest = text.substring(match.range.last + 1).trim()
    var unit = ""
    val word = Regex("""^([A-Za-z]+)\.?(?=\s|$)""").find(rest)
    if (word != null && word.groupValues[1].lowercase() in IngredientUnits) {
        unit = word.groupValues[1]
        rest = rest.substring(word.range.last + 1).trim()
    }
    rest = rest.removePrefix("of ").trim()
    val name = rest.substringBefore(',').trim()
    val note = rest.substringAfter(',', "").trim()
    return if (name.isEmpty()) RecipeIngredient(name = line.trim()) else RecipeIngredient(name, quantity, unit, note)
}
