package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class CookbookLogicTest {

    private fun step(id: String, vararg after: String) = RecipeStep(id, "do $id", after.toList())

    private fun stageIds(steps: List<RecipeStep>) = planSteps(steps).map { stage -> stage.map { it.step.id } }

    @Test
    fun stepsWithNothingToWaitForShareTheFirstStage() {
        // Arrange
        val steps = listOf(step("boil"), step("brown"), step("bake", "boil", "brown"))

        // Act
        val stages = stageIds(steps)

        // Assert
        assertEquals(listOf(listOf("boil", "brown"), listOf("bake")), stages)
    }

    @Test
    fun aStepLandsInTheStageAfterItsLatestPrerequisite() {
        // Arrange
        val steps = listOf(step("a"), step("b", "a"), step("c", "b"), step("d", "a"))

        // Act
        val stages = stageIds(steps)

        // Assert
        assertEquals(listOf(listOf("a"), listOf("b", "d"), listOf("c")), stages)
    }

    @Test
    fun stepsAreNumberedAcrossStagesAndNameTheirPrerequisitesByNumber() {
        // Arrange
        val steps = listOf(step("a"), step("b", "a"), step("c"), step("d", "b", "c"))

        // Act
        val plan = planSteps(steps).flatten()

        // Assert
        assertEquals(listOf("a" to 1, "c" to 2, "b" to 3, "d" to 4), plan.map { it.step.id to it.number })
        assertEquals(listOf(3, 2), plan.single { it.step.id == "d" }.afterNumbers.reversed())
    }

    @Test
    fun aPrerequisiteThatDoesNotExistIsIgnored() {
        // Arrange
        val steps = listOf(step("a", "gone", "a"), step("b", "a"))

        // Act
        val stages = stageIds(steps)

        // Assert
        assertEquals(listOf(listOf("a"), listOf("b")), stages)
    }

    @Test
    fun stepsTrappedInACycleAreStillShownInALastStage() {
        // Arrange
        val steps = listOf(step("free"), step("x", "y"), step("y", "x"))

        // Act
        val stages = stageIds(steps)

        // Assert
        assertEquals(listOf(listOf("free"), listOf("x", "y")), stages)
    }

    @Test
    fun noStepsPlanNothing() {
        // Arrange / Act / Assert
        assertEquals(emptyList(), planSteps(emptyList()))
    }

    @Test
    fun anIngredientIsInThePantryWhenItsStockIsAboveZeroIgnoringCaseAndPlurals() {
        // Arrange
        val pantry = listOf(PantryListItem("eggs", "Dairy", null, "Eggs", "", 6))

        // Act
        val state = availability(RecipeIngredient(" egg "), pantry)

        // Assert
        assertEquals(IngredientAvailability.InPantry, state)
    }

    @Test
    fun anIngredientWithNoStockIsOutOfStockAndAnUnknownOneIsNotInThePantry() {
        // Arrange
        val pantry = listOf(PantryListItem("flour", "Baking", null, "Flour", "", 0))

        // Act
        val known = availability(RecipeIngredient("Flour"), pantry)
        val unknown = availability(RecipeIngredient("Saffron"), pantry)

        // Assert
        assertEquals(IngredientAvailability.OutOfStock, known)
        assertEquals(IngredientAvailability.NotInPantry, unknown)
    }

    @Test
    fun quantitiesScaleWithServingsAndFormatWithoutTrailingZeros() {
        // Arrange / Act
        val doubled = scaleQuantity(1.5, from = 4, target = 8)
        val third = scaleQuantity(1.0, from = 3, target = 1)

        // Assert
        assertEquals("3", formatQuantity(doubled))
        assertEquals("0.33", formatQuantity(third))
        assertEquals("1.5", formatQuantity(1.5))
        assertEquals(5.0, scaleQuantity(5.0, from = 0, target = 3))
    }

    @Test
    fun minutesFormatAsHoursAndMinutes() {
        // Arrange / Act / Assert
        assertEquals("", formatMinutes(0))
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2 h", formatMinutes(120))
        assertEquals("1 h 5 min", formatMinutes(65))
    }

    @Test
    fun totalTimeIsPrepPlusCookPlusRest() {
        // Arrange
        val recipe = Recipe(prepMinutes = 10, cookMinutes = 30, restMinutes = 5)

        // Act / Assert
        assertEquals(45, recipe.totalMinutes)
    }

    @Test
    fun filteringByQueryMatchesTitleIngredientAndTagIgnoringCase() {
        // Arrange
        val pie = Recipe(key = "pie", title = "Shepherd's Pie", ingredients = listOf(RecipeIngredient("Lamb")))
        val soup = Recipe(key = "soup", title = "Soup", tags = listOf("Winter"))
        val recipes = listOf(pie, soup)

        // Act
        val byTitle = filterRecipes(recipes, CookbookFilter(query = "PIE"))
        val byIngredient = filterRecipes(recipes, CookbookFilter(query = "lamb"))
        val byTag = filterRecipes(recipes, CookbookFilter(query = "winter"))

        // Assert
        assertEquals(listOf("pie"), byTitle.map { it.key })
        assertEquals(listOf("pie"), byIngredient.map { it.key })
        assertEquals(listOf("soup"), byTag.map { it.key })
    }

    @Test
    fun filteringByTypeFavouritesAndTagsNarrowsTogether() {
        // Arrange
        val a = Recipe(key = "a", type = RecipeType.Dessert, favourite = true, tags = listOf("Quick"))
        val b = Recipe(key = "b", type = RecipeType.Dessert, favourite = false, tags = listOf("Quick"))
        val c = Recipe(key = "c", type = RecipeType.Dinner, favourite = true, tags = listOf("Quick"))

        // Act
        val result = filterRecipes(
            listOf(a, b, c),
            CookbookFilter(type = RecipeType.Dessert, favouritesOnly = true, tags = setOf("quick")),
        )

        // Assert
        assertEquals(listOf("a"), result.map { it.key })
    }
}
