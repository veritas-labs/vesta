package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_weekdays
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.meal_planner_day_heading
import vesta.shared.generated.resources.meal_planner_eaters_add
import vesta.shared.generated.resources.meal_planner_eaters_add_label
import vesta.shared.generated.resources.meal_planner_eaters_back
import vesta.shared.generated.resources.meal_planner_eaters_for
import vesta.shared.generated.resources.meal_planner_eaters_guests
import vesta.shared.generated.resources.meal_planner_eaters_members
import vesta.shared.generated.resources.meal_planner_eaters_no_guests
import vesta.shared.generated.resources.meal_planner_eaters_remove
import vesta.shared.generated.resources.meal_planner_eaters_rename
import vesta.shared.generated.resources.meal_planner_eaters_rename_label
import vesta.shared.generated.resources.meal_planner_eaters_save
import vesta.shared.generated.resources.meal_planner_eaters_title
import vesta.shared.generated.resources.meal_planner_pick_for

/** Stable hooks for UI tests to target, independent of displayed copy or names. */
internal object MealPlannerEatersTestTags {
    const val Screen = "meal_planner_eaters_screen"
    const val BackButton = "meal_planner_eaters_back"
    const val AddField = "meal_planner_eaters_add_field"
    const val AddButton = "meal_planner_eaters_add_button"
    const val RenameField = "meal_planner_eaters_rename_field"
    const val RenameSave = "meal_planner_eaters_rename_save"
    const val GuestsHint = "meal_planner_eaters_guests_hint"

    /** The row of a person, whose checkbox is [checkbox]. [ref] is the person's [MealPerson.ref]. */
    fun person(ref: String) = "meal_planner_eaters_person_$ref"

    fun checkbox(ref: String) = "meal_planner_eaters_checkbox_$ref"

    fun rename(ref: String) = "meal_planner_eaters_rename_$ref"

    fun remove(ref: String) = "meal_planner_eaters_remove_$ref"
}

/**
 * Who is eating the recipe called [recipeTitle] in [slot] on [date] (ADR-028): every [people] tickable, the Home's members first and then the guests (people who eat
 * with the Home without being in it), with [eating] (the [MealPerson.ref]s) ticked. A tick calls [onSetEaters] with the new set
 * straight away. A guest is added with the field at the bottom ([onAddGuest]), renamed by tapping their name ([onRenameGuest]) and
 * removed with the bin ([onRemoveGuest]), which un-tags them from every meal. Back calls [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerEatersScreen(
    date: CalendarDate,
    slot: MealSlot,
    recipeTitle: String,
    people: List<MealPerson>,
    eating: Set<String>,
    modifier: Modifier = Modifier,
    onSetEaters: (Set<String>) -> Unit = {},
    onAddGuest: (String) -> Unit = {},
    onRenameGuest: (guestId: String, name: String) -> Unit = { _, _ -> },
    onRemoveGuest: (guestId: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val weekday = stringArrayResource(Res.array.calendar_weekdays)[date.dayOfWeek]
    val subtitle = stringResource(
        Res.string.meal_planner_eaters_for,
        recipeTitle,
        stringResource(
            Res.string.meal_planner_pick_for,
            mealSlotLabel(slot),
            stringResource(Res.string.meal_planner_day_heading, weekday, date.day),
        ),
    )
    val members = people.filter { it.isMember }
    val guests = people.filter { !it.isMember }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(MealPlannerEatersTestTags.Screen)) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(Res.string.meal_planner_eaters_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerEatersTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.meal_planner_eaters_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "members-heading") { Heading(stringResource(Res.string.meal_planner_eaters_members)) }
                items(members, key = { it.ref }) { person ->
                    PersonRow(person, ticked = person.ref in eating, onToggle = { onSetEaters(eating.toggled(person.ref)) })
                }
                item(key = "guests-heading") { Heading(stringResource(Res.string.meal_planner_eaters_guests)) }
                if (guests.isEmpty()) {
                    item(key = "guests-hint") {
                        Text(
                            stringResource(Res.string.meal_planner_eaters_no_guests),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp).testTag(MealPlannerEatersTestTags.GuestsHint),
                        )
                    }
                }
                items(guests, key = { it.ref }) { person ->
                    val guestId = person.ref.removePrefix("p:")
                    if (renaming == guestId) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = { Text(stringResource(Res.string.meal_planner_eaters_rename_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag(MealPlannerEatersTestTags.RenameField),
                            )
                            TextButton(
                                onClick = {
                                    onRenameGuest(guestId, renameText)
                                    renaming = null
                                },
                                modifier = Modifier.testTag(MealPlannerEatersTestTags.RenameSave),
                            ) { Text(stringResource(Res.string.meal_planner_eaters_save)) }
                        }
                    } else {
                        PersonRow(
                            person,
                            ticked = person.ref in eating,
                            onToggle = { onSetEaters(eating.toggled(person.ref)) },
                            onRename = {
                                renameText = person.name
                                renaming = guestId
                            },
                            onRemove = { onRemoveGuest(guestId) },
                        )
                    }
                }
                item(key = "add") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(Res.string.meal_planner_eaters_add_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag(MealPlannerEatersTestTags.AddField),
                        )
                        Button(
                            onClick = {
                                onAddGuest(newName)
                                newName = ""
                            },
                            enabled = newName.isNotBlank(),
                            modifier = Modifier.testTag(MealPlannerEatersTestTags.AddButton),
                        ) { Text(stringResource(Res.string.meal_planner_eaters_add)) }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggled(ref: String): Set<String> = if (ref in this) this - ref else this + ref

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** One person with a checkbox; [onRename] and [onRemove], given for a guest, add a tappable name and a bin. */
@Composable
private fun PersonRow(
    person: MealPerson,
    ticked: Boolean,
    onToggle: () -> Unit,
    onRename: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(MealPlannerEatersTestTags.person(person.ref)),
    ) {
        Checkbox(checked = ticked, onCheckedChange = { onToggle() }, modifier = Modifier.testTag(MealPlannerEatersTestTags.checkbox(person.ref)))
        Text(
            person.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .let {
                    if (onRename == null) it.clickable(role = Role.Checkbox, onClick = onToggle)
                    else it.clickable(onClickLabel = stringResource(Res.string.meal_planner_eaters_rename, person.name), onClick = onRename)
                        .testTag(MealPlannerEatersTestTags.rename(person.ref))
                }
                .padding(vertical = 12.dp),
        )
        onRemove?.let {
            IconButton(onClick = it, modifier = Modifier.testTag(MealPlannerEatersTestTags.remove(person.ref))) {
                Icon(
                    painterResource(Res.drawable.ic_trash),
                    contentDescription = stringResource(Res.string.meal_planner_eaters_remove, person.name),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val PreviewDate = CalendarDate(2026, 10, 14)

private val PreviewPeople = listOf(
    MealPerson("m:mark", "Mark", isMember = true),
    MealPerson("m:sam", "Sam", isMember = true),
    MealPerson("p:ella", "Ella", isMember = false),
    MealPerson("p:noah", "Noah", isMember = false),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerEatersPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerEatersScreen(PreviewDate, MealSlot.Dinner, "Shepherd's Pie", PreviewPeople, eating = setOf("m:mark", "p:ella")) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerEatersPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerEatersScreen(PreviewDate, MealSlot.Dinner, "Shepherd's Pie", PreviewPeople, eating = setOf("m:mark", "p:ella")) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerEatersTabletLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerEatersScreen(PreviewDate, MealSlot.Dinner, "Shepherd's Pie", PreviewPeople, eating = setOf("m:mark", "p:ella")) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerEatersTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerEatersScreen(PreviewDate, MealSlot.Dinner, "Shepherd's Pie", PreviewPeople, eating = setOf("m:mark", "p:ella")) }
}
