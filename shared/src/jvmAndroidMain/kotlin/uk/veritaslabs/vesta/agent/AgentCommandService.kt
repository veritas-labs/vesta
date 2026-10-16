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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.getDeviceName
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xEventHub
import uk.veritaslabs.kits.x0x.createX0xClient
import uk.veritaslabs.kits.spaces.SpaceRepository

/**
 * App-scope wiring for ADR-009 agent commands. It hands out the one [PantrySources] per database —
 * which the UI must use too, so a command and a screen edit share one Automerge document — and runs
 * the loop that feeds direct messages from x0x to a [PantryCommandHandler].
 */
class AgentCommandService(
    private val allowlist: AgentAllowlist = EnvAgentAllowlist(),
    private val createClient: () -> X0xClient = ::createX0xClient,
) {
    private var currentSources: Pair<KitsDatabase, VestaHomeSources>? = null
    private var currentAssistants: Pair<KitsDatabase, AssistantRepository>? = null

    /**
     * The one [AssistantRepository] of [database], shared by the command handler and the UI: held commands waiting
     * for Allow once live in it, so both must see the same instance.
     */
    @Synchronized
    fun assistants(database: KitsDatabase): AssistantRepository =
        currentAssistants?.takeIf { it.first === database }?.second
            ?: AssistantRepository(database, allowlist).also { currentAssistants = database to it }

    /** The one set of per-Home document sources of [database], shared by the UI, the agent commands and the sync. */
    @Synchronized
    fun homeSources(database: KitsDatabase): VestaHomeSources =
        currentSources?.takeIf { it.first === database }?.second
            ?: VestaHomeSources(database).also { currentSources = database to it }

    /** The shared Pantry sources of [database]. */
    fun pantrySources(database: KitsDatabase): PantrySources = homeSources(database).pantries

    /** [homeId]'s shared Pantry source, for the UI; the Home must exist. */
    suspend fun pantry(database: KitsDatabase, homeId: String): PantryDocumentSource =
        checkNotNull(pantrySources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared shopping-list sources of [database]. */
    fun shoppingListSources(database: KitsDatabase): ShoppingListSources = homeSources(database).shoppingLists

    /** [homeId]'s shared shopping-list source, for the UI; the Home must exist. */
    suspend fun shoppingList(database: KitsDatabase, homeId: String): ShoppingListDocumentSource =
        checkNotNull(shoppingListSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared cookbook sources of [database]. */
    fun cookbookSources(database: KitsDatabase): CookbookSources = homeSources(database).cookbooks

    /** [homeId]'s shared cookbook source, for the UI; the Home must exist. */
    suspend fun cookbook(database: KitsDatabase, homeId: String): CookbookDocumentSource =
        checkNotNull(cookbookSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared merchants sources of [database]. */
    fun merchantsSources(database: KitsDatabase): MerchantsSources = homeSources(database).merchants

    /** [homeId]'s shared merchants source, for the UI; the Home must exist. */
    suspend fun merchants(database: KitsDatabase, homeId: String): MerchantsDocumentSource =
        checkNotNull(merchantsSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared meal plan sources of [database]. */
    fun mealPlanSources(database: KitsDatabase): MealPlanSources = homeSources(database).mealPlans

    /** [homeId]'s shared meal plan source, for the UI; the Home must exist. */
    suspend fun mealPlan(database: KitsDatabase, homeId: String): MealPlanDocumentSource =
        checkNotNull(mealPlanSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared calendar sources of [database]. */
    fun calendarSources(database: KitsDatabase): CalendarSources = homeSources(database).calendars

    /** [homeId]'s shared calendar source, for the UI; the Home must exist. */
    suspend fun calendar(database: KitsDatabase, homeId: String): CalendarDocumentSource =
        checkNotNull(calendarSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The user's private calendar source in [homeId], for the UI; the Home must exist. */
    suspend fun privateCalendar(database: KitsDatabase, homeId: String): CalendarDocumentSource =
        checkNotNull(calendarSources(database).privateForHome(homeId)) { "no Home with id $homeId" }

    /** The shared task sources of [database] (ADR-031). */
    fun taskSources(database: KitsDatabase): TaskSources = homeSources(database).tasks

    /** [homeId]'s shared tasks source, for the UI; the Home must exist. */
    suspend fun tasks(database: KitsDatabase, homeId: String): TaskDocumentSource =
        checkNotNull(taskSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The user's private tasks source in [homeId], for the UI; the Home must exist. */
    suspend fun privateTasks(database: KitsDatabase, homeId: String): TaskDocumentSource =
        checkNotNull(taskSources(database).privateForHome(homeId)) { "no Home with id $homeId" }

    /** Handles agent commands arriving on x0x until cancelled; never returns normally. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun run(database: KitsDatabase) = coroutineScope {
        val client = createClient()
        val sources = pantrySources(database)
        val handler = PantryCommandHandler(
            resolveSource = sources::forHome,
            resolveShoppingList = shoppingListSources(database)::forHome,
            resolveMealPlan = mealPlanSources(database)::forHome,
            listHomes = { HomeRepository(SpaceRepository(database, profiles = ProfileRepository(database, deviceName = ::getDeviceName))).homes.first().map { HomeRef(it.id, it.name) } },
            access = assistants(database),
            commandLog = DatabaseCommandLog(database),
            client = client,
            newId = { "urn:uuid:${Uuid.random()}" },
            resolveCookbook = cookbookSources(database)::forHome,
        )
        X0xEventHub(client).directMessages.collect { message ->
            // Its own coroutine so a slow reply doesn't hold up the stream; the handler serialises itself.
            launch { handler.handle(message) }
        }
    }
}
