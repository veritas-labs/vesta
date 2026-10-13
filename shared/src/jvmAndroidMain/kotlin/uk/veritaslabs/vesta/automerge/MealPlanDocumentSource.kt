package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
import uk.veritaslabs.vesta.ui.screens.MealPlan
import uk.veritaslabs.vesta.ui.screens.MealPlanSource
import uk.veritaslabs.vesta.ui.screens.MealSlot

private const val MealPlanFeature = "mealplan"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s meal plan document. */
fun mealPlanDocumentId(homeId: String): String = "$homeId:$MealPlanFeature"

/**
 * A [MealPlanSource] backed by a meal plan Automerge [document] (ADR-028). Same threading contract as
 * [CookbookDocumentSource]: route every read or write of [document] through this class. [nowMillis] stamps each planned
 * recipe so a slot lists them in the order they were added.
 *
 * Use [openMealPlanDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
@OptIn(ExperimentalUuidApi::class)
class MealPlanDocumentSource(
    private val document: Document,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newGuestId: () -> String = { Uuid.random().toString() },
) : MealPlanSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(document.readMealPlan())

    override val plan: Flow<MealPlan> = state

    override suspend fun add(date: CalendarDate, slot: MealSlot, recipeKey: String) =
        edit { planMeal(document, date, slot, recipeKey, nowMillis()) }

    override suspend fun remove(date: CalendarDate, slot: MealSlot, recipeKey: String) =
        edit { unplanMeal(document, date, slot, recipeKey) }

    override suspend fun setEaters(date: CalendarDate, slot: MealSlot, refs: Set<String>) =
        edit { chooseEaters(document, date, slot, refs) }

    override suspend fun addGuest(name: String): String? {
        val trimmed = name.trim().ifEmpty { return null }
        val id = newGuestId()
        edit { addMealGuest(document, id, trimmed); true }
        return id
    }

    override suspend fun renameGuest(id: String, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        edit { renameMealGuest(document, id, trimmed) }
    }

    override suspend fun removeGuest(id: String) = edit { removeMealGuest(document, id) }

    private suspend fun edit(change: () -> Boolean) {
        val changed = lock.withLock {
            change().also { if (it) publishAndPersist() }
        }
        if (changed) onLocalChange()
    }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the plan if it changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    /** The plan's current heads, saved as [encodeHeads] writes them (for the mailbox, ADR-020). */
    suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    /** The changes made since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

    private suspend fun applyIncoming(apply: Document.() -> Unit) {
        lock.withLock {
            val before = document.heads
            document.apply()
            if (!document.heads.contentEquals(before)) publishAndPersist()
        }
    }

    /** Republishes and persists after [document] changed outside [add]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        state.value = document.readMealPlan()
        persist(document.save())
    }
}

/**
 * Opens [homeId]'s meal plan as a [MealPlanDocumentSource] persisted in [database]'s `AutomergeDocument` table (ADR-007):
 * loads the stored document if there is one, otherwise starts a new empty plan and stores it straight away. The
 * [homeId] must be an existing `Home` row.
 */
suspend fun openMealPlanDocumentSource(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): MealPlanDocumentSource {
    val documentId = mealPlanDocumentId(homeId)
    val queries = database.automergeDocumentQueries
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val document = Document.load(stored?.bytes ?: Document().save())
    val source = MealPlanDocumentSource(
        document,
        persist = { bytes -> queries.upsert(documentId, homeId, MealPlanFeature, bytes, nowMillis()) },
        onLocalChange = onLocalChange,
        nowMillis = nowMillis,
    )
    if (stored == null) source.refresh()
    return source
}
