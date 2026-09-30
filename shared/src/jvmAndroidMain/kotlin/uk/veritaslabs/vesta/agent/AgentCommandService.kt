package uk.veritaslabs.vesta.agent

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.PantrySources
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

    /** The shared Pantry sources of [database]. */
    @Synchronized
    fun pantrySources(database: VestaDatabase): PantrySources =
        current?.takeIf { it.first === database }?.second
            ?: PantrySources(database).also { current = database to it }

    /** [homeId]'s shared Pantry source, for the UI; the Home must exist. */
    suspend fun pantry(database: VestaDatabase, homeId: String): PantryDocumentSource =
        checkNotNull(pantrySources(database).forHome(homeId)) { "no Home with id $homeId" }

    /** Handles agent commands arriving on x0x until cancelled; never returns normally. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun run(database: VestaDatabase) = coroutineScope {
        val client = createClient()
        val sources = pantrySources(database)
        val handler = PantryCommandHandler(
            resolveSource = sources::forHome,
            listHomes = { HomeRepository(database).homes.first().map { HomeRef(it.id, it.name) } },
            allowlist = allowlist,
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
