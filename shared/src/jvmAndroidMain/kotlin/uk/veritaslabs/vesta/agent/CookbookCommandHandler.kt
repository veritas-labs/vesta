package uk.veritaslabs.vesta.agent

import kotlinx.coroutines.flow.first
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.ui.screens.Recipe

/**
 * The cookbook half of [PantryCommandHandler] (ADR-027): reads answered fresh, adds and removes applied once per command id.
 * Every command is gated by the assistant's Cookbook Read or Write grant on that Home, exactly as Pantry commands are:
 * a missing grant looks the same whether or not the Home exists, Ask holds the command until Allow once.
 *
 * [PantryCommandHandler] has already checked the daemon's trust and that the sender is an active assistant.
 */
internal class CookbookCommandHandler(
    private val resolveSource: suspend (homeId: String) -> CookbookDocumentSource?,
    private val access: AssistantAccess,
    private val commandLog: CommandLog,
    private val send: suspend (agentId: String, replyJson: String) -> Unit,
    private val newId: () -> String,
    private val log: (String) -> Unit,
) {

    /** Answers a cookbook read; false if [command] is not one. Not recorded in the command log: a repeat is answered afresh. */
    suspend fun read(sender: String, command: AgentCommand): Boolean {
        val (homeId, summary) = when (command) {
            is ReadCookbook -> command.homeId to "Read the cookbook"
            is ReadRecipe -> command.homeId to "Read a recipe"
            else -> return false
        }
        if (!permitted(sender, homeId, AssistantCapability.CookbookRead, command.commandId, summary)) return true
        val source = resolveSource(homeId)
        if (source == null) {
            send(sender, reject(command.commandId, UnknownHome))
            access.recordActivity(sender, homeId, AssistantCapability.CookbookRead, command.commandId, ActivityOutcome.Rejected, "$summary: $UnknownHome")
            return true
        }
        val recipes = source.recipes.first()
        when (command) {
            is ReadCookbook -> {
                send(sender, CookbookReplies.cookbook(newId(), command.commandId, homeId, recipes.take(CookbookCommandParser.MAX_RECIPES), recipes.size))
                access.recordActivity(sender, homeId, AssistantCapability.CookbookRead, command.commandId, ActivityOutcome.Applied, "Read ${recipes.size} recipe(s)")
            }
            is ReadRecipe -> {
                val recipe = recipes.firstOrNull { it.key == command.recipeKey }
                if (recipe == null) {
                    send(sender, reject(command.commandId, UnknownRecipe))
                    access.recordActivity(sender, homeId, AssistantCapability.CookbookRead, command.commandId, ActivityOutcome.Rejected, "$summary: $UnknownRecipe")
                } else {
                    send(sender, CookbookReplies.recipe(newId(), command.commandId, recipe))
                    access.recordActivity(sender, homeId, AssistantCapability.CookbookRead, command.commandId, ActivityOutcome.Applied, "Read the recipe ${recipe.title}")
                }
            }
            else -> Unit
        }
        return true
    }

    /**
     * Applies a cookbook write; false if [command] is not one. The caller has already re-sent the reply of a command id
     * it answered before, so reaching here means a new command.
     */
    suspend fun write(sender: String, command: AgentCommand): Boolean {
        val (homeId, summary) = when (command) {
            is AddRecipe -> command.homeId to "Add the recipe ${command.recipe.title} to the cookbook"
            is RemoveRecipe -> command.homeId to "Remove the recipe ${command.recipeKey ?: command.title} from the cookbook"
            else -> return false
        }
        if (!permitted(sender, homeId, AssistantCapability.CookbookWrite, command.commandId, summary)) return true

        var rejection: String? = null
        val reply = apply(command) { rejection = it }
        commandLog.record(sender, command.commandId, reply)
        send(sender, reply)
        access.recordActivity(
            sender, homeId, AssistantCapability.CookbookWrite, command.commandId,
            if (rejection == null) ActivityOutcome.Applied else ActivityOutcome.Rejected,
            rejection?.let { "$summary: $it" } ?: summary,
        )
        return true
    }

    private suspend fun apply(command: AgentCommand, onReject: (String) -> Unit): String {
        fun refuse(reason: String) = reject(command.commandId, reason).also { onReject(reason) }
        return when (command) {
            is AddRecipe -> {
                val source = resolveSource(command.homeId) ?: return refuse(UnknownHome)
                val title = command.recipe.title
                if (source.recipes.first().any { it.title.trim().equals(title, ignoreCase = true) }) return refuse(RecipeExists)
                val key = source.save(command.recipe)
                CookbookReplies.done(newId(), command.commandId, key, title)
            }
            is RemoveRecipe -> {
                val source = resolveSource(command.homeId) ?: return refuse(UnknownHome)
                val recipe = source.recipes.first().findRecipe(command.recipeKey, command.title) ?: return refuse(UnknownRecipe)
                source.remove(recipe.key)
                CookbookReplies.done(newId(), command.commandId, recipe.key, recipe.title)
            }
            else -> refuse("not a cookbook command")
        }
    }

    private fun List<Recipe>.findRecipe(key: String?, title: String?): Recipe? =
        key?.let { k -> firstOrNull { it.key == k } }
            ?: title?.let { t -> firstOrNull { it.title.trim().equals(t.trim(), ignoreCase = true) } }

    /** True when the assistant may run [capability] in [homeId]; otherwise the agent has been told why (rejected or held). */
    private suspend fun permitted(sender: String, homeId: String, capability: AssistantCapability, commandId: String, summary: String): Boolean {
        when (access.decide(sender, homeId, capability, commandId)) {
            AccessDecision.Allow -> return true
            AccessDecision.NotPermitted -> {
                log("rejected command $commandId from $sender: no $capability grant for home $homeId")
                send(sender, reject(commandId, NotPermittedSummary))
                access.recordActivity(sender, homeId, capability, commandId, ActivityOutcome.Rejected, "$summary: $NotPermittedSummary")
            }
            AccessDecision.Ask -> {
                log("held command $commandId from $sender: waiting for the user to allow it")
                access.hold(sender, homeId, capability, commandId, summary)
                send(sender, reject(commandId, WaitingForApprovalSummary))
            }
        }
        return false
    }

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)

    private companion object {
        const val UnknownHome = "unknown home"
        const val UnknownRecipe = "unknown recipe"
        const val RecipeExists = "recipe already exists"
    }
}
