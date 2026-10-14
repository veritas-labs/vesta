package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.meal_planner_required_empty
import vesta.shared.generated.resources.meal_planner_required_title
import vesta.shared.generated.resources.common_back

/** One line of the week's shopping need: [name] with the total [quantity] of [unit], or no quantity when no recipe gave one. */
data class RequiredIngredient(val name: String, val quantity: Double?, val unit: String)

/**
 * What the recipes planned on [days] need in all, once per planned recipe (servings are not scaled). Lines with the same ingredient
 * (ignoring case and plurals) and unit are added together; an ingredient with no quantity shows once without one. Sorted by name.
 * A planned recipe no longer in [recipes] adds nothing.
 */
fun requiredIngredients(days: List<CalendarDate>, plan: MealPlan, recipes: List<Recipe>): List<RequiredIngredient> {
    val byKey = recipes.associateBy { it.key }
    val totals = LinkedHashMap<Pair<String, String>, RequiredIngredient>()
    days.forEach { date ->
        MealSlot.entries.forEach { slot ->
            plan.recipes(date, slot).mapNotNull { byKey[it] }.forEach { recipe ->
                recipe.ingredients.filter { it.name.isNotBlank() }.forEach { line ->
                    val unit = if (line.quantity == null) "" else line.unit.trim()
                    val id = ingredientKey(line.name) to unit.lowercase()
                    val seen = totals[id]
                    totals[id] = RequiredIngredient(
                        name = seen?.name ?: line.name.trim(),
                        quantity = line.quantity?.let { (seen?.quantity ?: 0.0) + it } ?: seen?.quantity,
                        unit = unit,
                    )
                }
            }
        }
    }
    return totals.values.sortedBy { it.name.lowercase() }
}

internal object MealPlannerRequiredTestTags {
    const val BackButton = "meal_planner_required_back"
    const val Empty = "meal_planner_required_empty"

    fun item(name: String) = "meal_planner_required_item_${ingredientKey(name)}"
}

/** The ingredients [items] needed to cook the week shown as [weekRange], as a full-screen vertical list, name left and amount right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerRequiredItemsScreen(
    items: List<RequiredIngredient>,
    weekRange: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(Res.string.meal_planner_required_title))
                    Text(weekRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerRequiredTestTags.BackButton)) {
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.meal_planner_required_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp).testTag(MealPlannerRequiredTestTags.Empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(items, key = { ingredientKey(it.name) + "|" + it.unit.lowercase() }) { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(MealPlannerRequiredTestTags.item(item.name)),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            item.quantity?.let {
                                Text(
                                    text = listOf(formatQuantity(it), item.unit).filter { part -> part.isNotEmpty() }.joinToString(" "),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private val PreviewItems = listOf(
    RequiredIngredient("Butter", 250.0, "g"),
    RequiredIngredient("Eggs", 6.0, ""),
    RequiredIngredient("Flour", 1.2, "kg"),
    RequiredIngredient("Salt", null, ""),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerRequiredItemsPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerRequiredItemsScreen(PreviewItems, "12 Oct – 18 Oct 2026") }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerRequiredItemsPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerRequiredItemsScreen(PreviewItems, "12 Oct – 18 Oct 2026") }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerRequiredItemsTabletLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerRequiredItemsScreen(PreviewItems, "12 Oct – 18 Oct 2026") }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerRequiredItemsTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerRequiredItemsScreen(PreviewItems, "12 Oct – 18 Oct 2026") }
}
