package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeType

class CookbookCommandParserTest {

    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private fun read(objectId: String) =
        """{"@context": $context, "type": "Read", "id": "urn:uuid:r1", "object": {"type": "Collection", "id": "$objectId"}}""".encodeToByteArray()

    private fun add(recipe: String, type: String = "Add", target: String = "urn:vesta:document:home-1:cookbook") =
        """{"@context": $context, "type": "$type", "id": "urn:uuid:a1", "target": {"type": "Collection", "id": "$target"}, "object": $recipe}""".encodeToByteArray()

    private fun recipe(fields: String) = """{"type": "schema:Recipe", $fields}"""

    private fun parsed(payload: ByteArray) = assertIs<ParseResult.Command>(AgentCommandParser.parse(payload)).command

    private fun invalid(payload: ByteArray) = assertIs<ParseResult.Invalid>(AgentCommandParser.parse(payload))

    @Test
    fun parsesAReadOfTheCookbookAndOfOneRecipe() {
        // Arrange
        val whole = read("urn:vesta:document:home-1:cookbook")
        val one = read("urn:vesta:document:home-1:cookbook:recipe-9")

        // Act
        val wholeResult = parsed(whole)
        val oneResult = parsed(one)

        // Assert
        assertEquals(ReadCookbook("urn:uuid:r1", "home-1"), wholeResult)
        assertEquals(ReadRecipe("urn:uuid:r1", "home-1", "recipe-9"), oneResult)
    }

    @Test
    fun parsesAFullRecipeAdd() {
        // Arrange
        val payload = add(
            recipe(
                """"schema:name": " Pancakes ", "schema:description": "Fluffy", "schema:recipeCategory": "breakfast", "schema:recipeYield": 6,
                   "schema:prepTime": "PT10M", "schema:cookTime": "PT1H30M", "schema:keywords": "quick, sweet",
                   "schema:isBasedOn": "https://example.com/p",
                   "schema:recipeIngredient": ["2 eggs", {"type": "schema:QuantitativeValue", "schema:name": "Flour", "schema:value": 200, "schema:unitText": "g", "schema:description": "plain"}],
                   "schema:recipeInstructions": ["Mix", {"type": "schema:HowToStep", "schema:text": "Fry", "schema:performTime": "PT5M"}]""",
            ),
        )

        // Act
        val command = assertIs<AddRecipe>(parsed(payload))

        // Assert
        val recipe = command.recipe
        assertEquals("home-1", command.homeId)
        assertEquals("Pancakes", recipe.title)
        assertEquals(RecipeType.Breakfast, recipe.type)
        assertEquals(6, recipe.servings)
        assertEquals(10, recipe.prepMinutes)
        assertEquals(90, recipe.cookMinutes)
        assertEquals(listOf("quick", "sweet"), recipe.tags)
        assertEquals(listOf(RecipeIngredient("2 eggs"), RecipeIngredient("Flour", 200.0, "g", "plain")), recipe.ingredients)
        assertEquals(listOf("Mix", "Fry"), recipe.steps.map { it.text })
        assertEquals(listOf(null, 5), recipe.steps.map { it.minutes })
    }

    @Test
    fun aRecipeWithOnlyANameGetsDefaults() {
        // Arrange
        val payload = add(recipe(""""schema:name": "Toast""""))

        // Act
        val recipe = assertIs<AddRecipe>(parsed(payload)).recipe

        // Assert
        assertEquals(RecipeType.Dinner, recipe.type)
        assertEquals(4, recipe.servings)
        assertEquals(emptyList(), recipe.ingredients)
    }

    @Test
    fun parsesARemoveByIdentifierOrByName() {
        // Arrange
        val byId = add(recipe(""""schema:identifier": "k1""""), type = "Remove")
        val byName = add(recipe(""""schema:name": "Toast""""), type = "Remove")

        // Act
        val first = parsed(byId)
        val second = parsed(byName)

        // Assert
        assertEquals(RemoveRecipe("urn:uuid:a1", "home-1", "k1", null), first)
        assertEquals(RemoveRecipe("urn:uuid:a1", "home-1", null, "Toast"), second)
    }

    @Test
    fun aPantryTargetIsStillAPantryCommand() {
        // Arrange
        val payload = read("urn:vesta:document:home-1:pantry")

        // Act
        val command = parsed(payload)

        // Assert
        assertEquals(ReadPantry("urn:uuid:r1", "home-1"), command)
    }

    @Test
    fun rejectsBadRecipes() {
        // Arrange
        val cases = mapOf(
            "title is required" to add(recipe(""""schema:description": "x"""")),
            "recipeYield out of range" to add(recipe(""""schema:name": "A", "schema:recipeYield": 0""")),
            "recipeYield must be an integer" to add(recipe(""""schema:name": "A", "schema:recipeYield": "4"""")),
            "prepTime must be an ISO 8601 duration" to add(recipe(""""schema:name": "A", "schema:prepTime": "soon"""")),
            "cookTime must be an ISO 8601 duration" to add(recipe(""""schema:name": "A", "schema:cookTime": "PT99999H"""")),
            "each recipeIngredient" to add(recipe(""""schema:name": "A", "schema:recipeIngredient": [{"schema:value": 1}]""")),
            "recipeIngredient must be an array" to add(recipe(""""schema:name": "A", "schema:recipeIngredient": "eggs"""")),
            "each recipeInstructions" to add(recipe(""""schema:name": "A", "schema:recipeInstructions": [3]""")),
            "title too long" to add(recipe(""""schema:name": "${"x".repeat(201)}"""")),
            "object must be a schema:Recipe" to add("""{"type": "schema:Product", "schema:name": "A"}"""),
            "schema:identifier or schema:name is required" to add(recipe(""""schema:description": "x""""), type = "Remove"),
        )

        // Act / Assert
        cases.forEach { (expected, payload) ->
            val result = invalid(payload)
            assertEquals("urn:uuid:a1", result.commandId)
            assertEquals(true, result.reason.contains(expected), "'${result.reason}' should mention '$expected'")
        }
    }

    @Test
    fun rejectsTooManyIngredients() {
        // Arrange
        val ingredients = List(CookbookCommandParser.MAX_INGREDIENTS + 1) { "\"i$it\"" }.joinToString()
        val payload = add(recipe(""""schema:name": "A", "schema:recipeIngredient": [$ingredients]"""))

        // Act
        val result = invalid(payload)

        // Assert
        assertEquals("too many ingredients", result.reason)
    }
}
