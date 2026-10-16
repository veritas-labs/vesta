package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.automerge.Document
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.docs.homeDocumentId
import uk.veritaslabs.vesta.docs.openHomeDocument
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.ui.screens.MealPlan
import uk.veritaslabs.vesta.ui.screens.MealPlanSource
import uk.veritaslabs.vesta.ui.screens.MealSlot

private const val MealPlanFeature = "mealplan"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s meal plan document. */
fun mealPlanDocumentId(homeId: String): String = homeDocumentId(homeId, MealPlanFeature)

/**
 * A [MealPlanSource] backed by a meal plan Automerge [document] (ADR-028). Same threading contract as
 * [CookbookDocumentSource]: route every read or write of [document] through this class. [nowMillis] stamps each planned
 * recipe so a slot lists them in the order they were added.
 *
 * Use [openMealPlanDocumentSource] to get one that loads and saves a Home's document in [KitsDatabase].
 */
@OptIn(ExperimentalUuidApi::class)
class MealPlanDocumentSource private constructor(
    private val core: AutomergeDocumentCore,
    private val nowMillis: () -> Long,
    private val newGuestId: () -> String,
    override val plan: Flow<MealPlan>,
) : MealPlanSource, HomeDocument by core {

    constructor(
        document: Document,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        newGuestId: () -> String = { Uuid.random().toString() },
    ) : this(MutableStateFlow(document.readMealPlan()), document, persist, onLocalChange, nowMillis, newGuestId)

    private constructor(
        state: MutableStateFlow<MealPlan>,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
        nowMillis: () -> Long,
        newGuestId: () -> String,
    ) : this(
        AutomergeDocumentCore(document, { state.value = document.readMealPlan() }, persist, onLocalChange),
        nowMillis,
        newGuestId,
        state,
    )

    override suspend fun add(date: CalendarDate, slot: MealSlot, recipeKey: String) =
        edit { planMeal(this, date, slot, recipeKey, nowMillis()) }

    override suspend fun remove(date: CalendarDate, slot: MealSlot, recipeKey: String) =
        edit { unplanMeal(this, date, slot, recipeKey) }

    override suspend fun setEaters(date: CalendarDate, slot: MealSlot, recipeKey: String, refs: Set<String>) =
        edit { chooseEaters(this, date, slot, recipeKey, refs) }

    override suspend fun addGuest(name: String): String? {
        val trimmed = name.trim().ifEmpty { return null }
        val id = newGuestId()
        edit { addMealGuest(this, id, trimmed); true }
        return id
    }

    override suspend fun renameGuest(id: String, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        edit { renameMealGuest(this, id, trimmed) }
    }

    override suspend fun removeGuest(id: String) = edit { removeMealGuest(this, id) }

    /** Publishes and persists only when [change] reports it changed something, as the plan's edits always did. */
    private suspend fun edit(change: Document.() -> Boolean) {
        core.editIfChanged(change)
    }

    /** Republishes and persists after the document changed outside the edits above, e.g. by applying a sync message. */
    suspend fun refresh() = core.refresh()
}

/**
 * Opens [homeId]'s meal plan as a [MealPlanDocumentSource] persisted in [database]'s `AutomergeDocument` table (ADR-007):
 * loads the stored document if there is one, otherwise starts a new empty plan and stores it straight away. The
 * [homeId] must be an existing `Home` row.
 */
suspend fun openMealPlanDocumentSource(
    database: KitsDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): MealPlanDocumentSource = checkNotNull(
    openHomeDocument(
        database, homeId, MealPlanFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> MealPlanDocumentSource(document, persist, changed, nowMillis) },
        onLocalChange = onLocalChange,
    ),
)
