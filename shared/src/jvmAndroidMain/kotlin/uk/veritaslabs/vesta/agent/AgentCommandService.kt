package uk.veritaslabs.vesta.agent

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MerchantsDocumentSource
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.home.HomeRepository
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
    private var current: Pair<VestaDatabase, PantrySources>? = null
    private var currentShopping: Pair<VestaDatabase, ShoppingListSources>? = null
    private var currentCookbook: Pair<VestaDatabase, CookbookSources>? = null
    private var currentMerchants: Pair<VestaDatabase, MerchantsSources>? = null
    private var currentAssistants: Pair<VestaDatabase, AssistantRepository>? = null

    /**
     * The one [AssistantRepository] of [database], shared by the command handler and the UI: held commands waiting
     * for Allow once live in it, so both must see the same instance.
     */
    @Synchronized
    fun assistants(database: VestaDatabase): AssistantRepository =
        currentAssistants?.takeIf { it.first === database }?.second
            ?: AssistantRepository(database, allowlist).also { currentAssistants = database to it }

    /** The shared Pantry sources of [database]. */
    @Synchronized
    fun pantrySources(database: VestaDatabase): PantrySources =
        current?.takeIf { it.first === database }?.second
            ?: PantrySources(database).also { current = database to it }

    /** [homeId]'s shared Pantry source, for the UI; the Home must exist. */
    suspend fun pantry(database: VestaDatabase, homeId: String): PantryDocumentSource =
        checkNotNull(pantrySources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared shopping-list sources of [database]. */
    fun shoppingListSources(database: VestaDatabase): ShoppingListSources {
        val pantry = pantrySources(database)
        return synchronized(this) {
            currentShopping?.takeIf { it.first === database }?.second
                ?: ShoppingListSources(database, pantry).also { currentShopping = database to it }
        }
    }

    /** [homeId]'s shared shopping-list source, for the UI; the Home must exist. */
    suspend fun shoppingList(database: VestaDatabase, homeId: String): ShoppingListDocumentSource =
        checkNotNull(shoppingListSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared cookbook sources of [database]. */
    @Synchronized
    fun cookbookSources(database: VestaDatabase): CookbookSources =
        currentCookbook?.takeIf { it.first === database }?.second
            ?: CookbookSources(database).also { currentCookbook = database to it }

    /** [homeId]'s shared cookbook source, for the UI; the Home must exist. */
    suspend fun cookbook(database: VestaDatabase, homeId: String): CookbookDocumentSource =
        checkNotNull(cookbookSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** The shared merchants sources of [database]. */
    @Synchronized
    fun merchantsSources(database: VestaDatabase): MerchantsSources =
        currentMerchants?.takeIf { it.first === database }?.second
            ?: MerchantsSources(database).also { currentMerchants = database to it }

    /** [homeId]'s shared merchants source, for the UI; the Home must exist. */
    suspend fun merchants(database: VestaDatabase, homeId: String): MerchantsDocumentSource =
        checkNotNull(merchantsSources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** Handles agent commands arriving on x0x until cancelled; never returns normally. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun run(database: VestaDatabase) = coroutineScope {
        val client = createClient()
        val sources = pantrySources(database)
        val handler = PantryCommandHandler(
            resolveSource = sources::forHome,
            listHomes = { HomeRepository(database).homes.first().map { HomeRef(it.id, it.name) } },
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
