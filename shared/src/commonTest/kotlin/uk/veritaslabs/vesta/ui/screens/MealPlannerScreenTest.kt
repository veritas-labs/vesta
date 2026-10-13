package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerScreenTest {

    // 2026-10-14 is a Wednesday, so a Monday week is 12–18 October.
    private val today = CalendarDate(2026, 10, 14)
    private val monday = CalendarDate(2026, 10, 12)
    private val pie = Recipe(key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner)
    private val porridge = Recipe(key = "porridge", title = "Porridge", type = RecipeType.Breakfast)
    private val soup = Recipe(key = "soup", title = "Soup", type = RecipeType.Lunch, tags = listOf("Quick"))

    private fun planOf(vararg entries: Triple<CalendarDate, MealSlot, List<String>>) =
        MealPlan(entries.groupBy { it.first }.mapValues { (_, day) -> day.associate { it.second to it.third } })

    @Test
    fun showsTheWeekContainingTodayStartingOnTheChosenDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), today = today, weekStart = WeekStart.Monday) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 12))).assertExists()
        onNodeWithTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 19))).assertDoesNotExist()
    }

    @Test
    fun aWeekOffsetShowsAnotherWeekAndOffersAWayBack() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, weekOffset = 1) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("19 Oct – 25 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.ThisWeek).assertIsDisplayed()
    }

    @Test
    fun thisWeekButtonIsHiddenOnTheCurrentWeek() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, weekOffset = 0) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.ThisWeek).assertDoesNotExist()
    }

    @Test
    fun theWeekControlsReportTheirTaps() = runComposeUiTest {
        // Arrange
        val taps = mutableListOf<String>()
        setContent {
            VestaTheme {
                MealPlannerScreen(
                    recipes = emptyList(), today = today, weekOffset = 2,
                    onPreviousWeek = { taps += "previous" }, onNextWeek = { taps += "next" }, onThisWeek = { taps += "this" },
                )
            }
        }

        // Act
        onNodeWithTag(MealPlannerTestTags.PreviousWeek).performClick()
        onNodeWithTag(MealPlannerTestTags.NextWeek).performClick()
        onNodeWithTag(MealPlannerTestTags.ThisWeek).performClick()

        // Assert
        assertEquals(listOf("previous", "next", "this"), taps)
    }

    @Test
    fun plannedRecipesShowInTheirDayAndSlotByTitle() = runComposeUiTest {
        // Arrange
        val plan = planOf(Triple(monday, MealSlot.Dinner, listOf("pie")))
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), plan = plan, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, "pie"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Shepherd's Pie").assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Lunch, "pie")).assertDoesNotExist()
    }

    @Test
    fun aRecipeNoLongerInTheCookbookShowsAsRemovedAndCanStillBeTakenOut() = runComposeUiTest {
        // Arrange
        var removed: Triple<CalendarDate, MealSlot, String>? = null
        val plan = planOf(Triple(monday, MealSlot.Lunch, listOf("gone")))
        setContent {
            VestaTheme {
                MealPlannerScreen(recipes = listOf(pie), plan = plan, today = today, onRemove = { d, s, k -> removed = Triple(d, s, k) })
            }
        }

        // Act
        onNodeWithText("Recipe removed").assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.removeButton(monday, MealSlot.Lunch, "gone")).performClick()

        // Assert
        assertEquals(Triple(monday, MealSlot.Lunch, "gone"), removed)
    }

    @Test
    fun tappingPlusReportsTheDayAndMealToAddTo() = runComposeUiTest {
        // Arrange
        var requested: Pair<CalendarDate, MealSlot>? = null
        setContent {
            VestaTheme { MealPlannerScreen(recipes = listOf(pie), today = today, onAddRecipe = { d, s -> requested = d to s }) }
        }

        // Act
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Breakfast)).performClick()

        // Assert
        assertEquals(monday to MealSlot.Breakfast, requested)
    }

    @Test
    fun backReportsATap() = runComposeUiTest {
        // Arrange
        var backed = false
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, onBack = { backed = true }) } }

        // Act
        onNodeWithTag(MealPlannerTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
    }

    private val people = listOf(
        MealPerson("m:mark", "Mark", isMember = true),
        MealPerson("m:sam", "Sam", isMember = true),
        MealPerson("p:ella", "Ella", isMember = false),
    )
    private val dinnerWithPie = mapOf(MealSlot.Dinner to listOf("pie"))

    private fun plannedPie(vararg eaters: Pair<String, Set<String>>) =
        MealPlan(days = mapOf(monday to dinnerWithPie), eaters = eaters.associate { PlannedMeal(monday, MealSlot.Dinner, it.first) to it.second })

    @Test
    fun aPlannedRecipeNobodyChoseEatersForSaysEveryone() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(listOf(pie), plan = plannedPie(), people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).assertTextEquals("Everyone")
    }

    @Test
    fun aPlannedRecipeWithChosenEatersNamesThem() = runComposeUiTest {
        // Arrange
        val plan = plannedPie("pie" to setOf("m:sam", "p:ella"))
        setContent { VestaTheme { MealPlannerScreen(listOf(pie), plan = plan, people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).assertTextEquals("Sam, Ella")
    }

    @Test
    fun aRecipeWhoseEatersAreAllTheMembersAndNobodyElseSaysEveryone() = runComposeUiTest {
        // Arrange
        val plan = plannedPie("pie" to setOf("m:mark", "m:sam"))
        setContent { VestaTheme { MealPlannerScreen(listOf(pie), plan = plan, people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).assertTextEquals("Everyone")
    }

    @Test
    fun aRecipeWithNoEatersChosenSaysNobody() = runComposeUiTest {
        // Arrange
        val plan = plannedPie("pie" to emptySet())
        setContent { VestaTheme { MealPlannerScreen(listOf(pie), plan = plan, people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).assertTextEquals("Nobody")
    }

    @Test
    fun twoRecipesInAMealShowTheirOwnEaters() = runComposeUiTest {
        // Arrange
        val plan = MealPlan(
            days = mapOf(monday to mapOf(MealSlot.Dinner to listOf("pie", "porridge"))),
            eaters = mapOf(PlannedMeal(monday, MealSlot.Dinner, "porridge") to setOf("p:ella")),
        )
        setContent { VestaTheme { MealPlannerScreen(listOf(pie, porridge), plan = plan, people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).assertTextEquals("Everyone")
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "porridge")).assertTextEquals("Ella")
    }

    @Test
    fun aRemovedRecipeHasNoEatersToEdit() = runComposeUiTest {
        // Arrange
        val plan = MealPlan(days = mapOf(monday to mapOf(MealSlot.Lunch to listOf("gone"))))
        setContent { VestaTheme { MealPlannerScreen(emptyList(), plan = plan, people = people, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Lunch, "gone")).assertDoesNotExist()
    }

    @Test
    fun tappingTheEatersReportsTheDayMealAndRecipe() = runComposeUiTest {
        // Arrange
        var requested: Triple<CalendarDate, MealSlot, String>? = null
        setContent {
            VestaTheme {
                MealPlannerScreen(
                    listOf(pie), plan = plannedPie(), people = people, today = today,
                    onEditEaters = { d, s, k -> requested = Triple(d, s, k) },
                )
            }
        }

        // Act
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, "pie")).performClick()

        // Assert
        assertEquals(Triple(monday, MealSlot.Dinner, "pie"), requested)
    }

    @Test
    fun eachDaySaysHowManyRecipesItHas() = runComposeUiTest {
        // Arrange
        val plan = MealPlan(
            days = mapOf(
                monday to mapOf(MealSlot.Dinner to listOf("pie")),
                monday.plusDays(1) to mapOf(MealSlot.Lunch to listOf("soup"), MealSlot.Dinner to listOf("pie")),
            ),
        )
        setContent { VestaTheme { MealPlannerScreen(listOf(pie, soup), plan = plan, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("1 recipe planned").assertIsDisplayed()
        onNodeWithText("2 recipes planned").assertIsDisplayed()
        onNodeWithText("No meals planned yet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPlannedRecipeShowsItsPhotoWhenItHasOne() = runComposeUiTest {
        // Arrange
        val photo = BlobReference(hash = "a".repeat(64), size = 1_000, mime = "image/webp", kind = MediaKind.Photo, w = 4, h = 3, blur = "")
        val withPhoto = pie.copy(media = listOf(RecipeMedia("", MediaKind.Photo, blob = photo)))
        val plan = MealPlan(days = mapOf(monday to mapOf(MealSlot.Dinner to listOf("pie"), MealSlot.Lunch to listOf("soup"))))
        setContent { VestaTheme { MealPlannerScreen(listOf(withPhoto, soup), plan = plan, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.thumbnail(monday, MealSlot.Dinner, "pie"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.thumbnail(monday, MealSlot.Lunch, "soup"), useUnmergedTree = true).assertDoesNotExist()
    }
}
