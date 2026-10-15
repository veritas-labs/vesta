package uk.veritaslabs.vesta.agent

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.automerge.CalendarDocumentSource
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.TaskDocumentSource
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MealPlanDocumentSource
import uk.veritaslabs.vesta.automerge.MealPlanSources
import uk.veritaslabs.vesta.automerge.MerchantsDocumentSource
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.getDeviceName
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xEventHub
import uk.veritaslabs.vesta.x0x.createX0xClient

/**
 * App-scope wiring for ADR-009 agent commands. It hands out the one [PantrySources] per database —
 * which the UI must use too, so a command and a screen edit share one Automerge document — and runs
 * the loop that feeds direct messages from x0x to a [PantryCommandHandler].
 */
class AgentCommandService(
    private val allowlist: AgentAllowlist = EnvAgentAllowlist(),
    private val createClient: () -> X0xClient = ::createX0xClient,
) {
    private var currentSources: Pair<VestaDatabase, VestaHomeSources>? = null
    private var currentAssistants: Pair<VestaDatabase, AssistantRepository>? = null

    /**
     * The one [AssistantRepository] of [database], shared by the command handler and the UI: held commands waiting
     * for Allow once live in it, so both must see the same instance.
     */
    @Synchronized
    fun assistants(database: VestaDatabase): AssistantRepository =
        currentAssistants?.takeIf { it.first === database }?.second
            ?: AssistantRepository(database, allowlist).also { currentAssistants = database to it }

    /** The one set of per-Home document sources of [database], shared by the UI, the agent commands and the sync. */
    @Synchronized
    fun homeSources(database: VestaDatabase): VestaHomeSources =
        currentSources?.takeIf { it.first === database }?.second
            ?: VestaHomeSources(database).also { currentSources = database to it }

    /** The shared Pantry sources of [database]. */
    fun pantrySources(database: VestaDatabase): PantrySources = homeSources(database).pantries

    /** [homeId]'s shared Pantry source, for the UI; the Home must exist. */
    suspend fun pantry(database: VestaDatabase, homeId: String): PantryDocumentSource =
        checkNotNull(pantrySources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared shopping-list sources of [database]. */
    fun shoppingListSources(database: VestaDatabase): ShoppingListSources = homeSources(database).shoppingLists

    /** [homeId]'s shared shopping-list source, for the UI; the Home must exist. */
    suspend fun shoppingList(database: VestaDatabase, homeId: String): ShoppingListDocumentSource =
        checkNotNull(shoppingListSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared cookbook sources of [database]. */
    fun cookbookSources(database: VestaDatabase): CookbookSources = homeSources(database).cookbooks

    /** [homeId]'s shared cookbook source, for the UI; the Home must exist. */
    suspend fun cookbook(database: VestaDatabase, homeId: String): CookbookDocumentSource =
        checkNotNull(cookbookSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared merchants sources of [database]. */
    fun merchantsSources(database: VestaDatabase): MerchantsSources = homeSources(database).merchants

    /** [homeId]'s shared merchants source, for the UI; the Home must exist. */
    suspend fun merchants(database: VestaDatabase, homeId: String): MerchantsDocumentSource =
        checkNotNull(merchantsSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared meal plan sources of [database]. */
    fun mealPlanSources(database: VestaDatabase): MealPlanSources = homeSources(database).mealPlans

    /** [homeId]'s shared meal plan source, for the UI; the Home must exist. */
    suspend fun mealPlan(database: VestaDatabase, homeId: String): MealPlanDocumentSource =
        checkNotNull(mealPlanSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared calendar sources of [database]. */
    fun calendarSources(database: VestaDatabase): CalendarSources = homeSources(database).calendars

    /** [homeId]'s shared calendar source, for the UI; the Home must exist. */
    suspend fun calendar(database: VestaDatabase, homeId: String): CalendarDocumentSource =
        checkNotNull(calendarSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The user's private calendar source in [homeId], for the UI; the Home must exist. */
    suspend fun privateCalendar(database: VestaDatabase, homeId: String): CalendarDocumentSource =
        checkNotNull(calendarSources(database).privateForHome(homeId)) { "no Home with id $homeId" }

    /** The shared task sources of [database] (ADR-031). */
    fun taskSources(database: VestaDatabase): TaskSources = homeSources(database).tasks

    /** [homeId]'s shared tasks source, for the UI; the Home must exist. */
    suspend fun tasks(database: VestaDatabase, homeId: String): TaskDocumentSource =
        checkNotNull(taskSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The user's private tasks source in [homeId], for the UI; the Home must exist. */
    suspend fun privateTasks(database: VestaDatabase, homeId: String): TaskDocumentSource =
        checkNotNull(taskSources(database).privateForHome(homeId)) { "no Home with id $homeId" }

    /** Handles agent commands arriving on x0x until cancelled; never returns normally. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun run(database: VestaDatabase) = coroutineScope {
        val client = createClient()
        val sources = pantrySources(database)
        val handler = PantryCommandHandler(
            resolveSource = sources::forHome,
            listHomes = { HomeRepository(database, profiles = ProfileRepository(database, deviceName = ::getDeviceName)).homes.first().map { HomeRef(it.id, it.name) } },
            access = assistants(database),
            commandLog = DatabaseCommandLog(database),
            client = client,
            newId = { "urn:uuid:${Uuid.random()}" },
        )
        X0xEventHub(client).directMessages.collect { message ->
            // Its own coroutine so a slow reply doesn't hold up the stream; the handler serialises itself.
            launch { handler.handle(message) }
        }
    }
}
