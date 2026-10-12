package uk.veritaslabs.vesta.mailbox

import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.cookbookDocumentId
import uk.veritaslabs.vesta.automerge.merchantsDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.rosterDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.datasync.HomeRosters

/** An Automerge document of a Home as the mailbox sees it, whoever owns and persists it. */
internal interface MailboxDocument {
    /** The document's current heads (see `encodeHeads`). */
    suspend fun heads(): ByteArray

    /** The changes made since [saved] heads, or all of them for none. */
    suspend fun changesSince(saved: ByteArray): ByteArray

    /** Applies changes from another device's [changesSince]. Applying them again changes nothing. */
    suspend fun applyChanges(changes: ByteArray)
}

/** The documents a Home's mailbox carries. */
internal interface MailboxDocuments {
    /** The ids of [homeId]'s documents, as the mailbox names them in its entries. */
    fun documentIds(homeId: String): List<String>

    /** [documentId] of [homeId], or null when this device has no such Home or this version doesn't know the document. */
    suspend fun open(homeId: String, documentId: String): MailboxDocument?
}

/** A Home's Pantry, shopping list, cookbook, merchants and roster. */
internal class HomeMailboxDocuments(
    private val pantries: PantrySources,
    private val shoppingLists: ShoppingListSources,
    private val cookbooks: CookbookSources,
    private val rosters: HomeRosters,
    private val merchants: MerchantsSources? = null,
) : MailboxDocuments {

    override fun documentIds(homeId: String) = listOf(rosterDocumentId(homeId), pantryDocumentId(homeId), shoppingListDocumentId(homeId), cookbookDocumentId(homeId)) +
        listOfNotNull(merchants?.let { merchantsDocumentId(homeId) })

    override suspend fun open(homeId: String, documentId: String): MailboxDocument? = when (documentId) {
        rosterDocumentId(homeId) -> object : MailboxDocument {
            override suspend fun heads() = rosters.heads(homeId) ?: ByteArray(0)
            override suspend fun changesSince(saved: ByteArray) = rosters.changesSince(homeId, saved) ?: ByteArray(0)
            override suspend fun applyChanges(changes: ByteArray) { rosters.applyChanges(homeId, changes) }
        }.takeIf { rosters.heads(homeId) != null }
        pantryDocumentId(homeId) -> pantries.forHome(homeId)?.let { source ->
            object : MailboxDocument {
                override suspend fun heads() = source.heads()
                override suspend fun changesSince(saved: ByteArray) = source.changesSince(saved)
                override suspend fun applyChanges(changes: ByteArray) = source.applyChanges(changes)
            }
        }
        shoppingListDocumentId(homeId) -> shoppingLists.forHome(homeId)?.let { source ->
            object : MailboxDocument {
                override suspend fun heads() = source.heads()
                override suspend fun changesSince(saved: ByteArray) = source.changesSince(saved)
                override suspend fun applyChanges(changes: ByteArray) = source.applyChanges(changes)
            }
        }
        cookbookDocumentId(homeId) -> cookbooks.forHome(homeId)?.let { source ->
            object : MailboxDocument {
                override suspend fun heads() = source.heads()
                override suspend fun changesSince(saved: ByteArray) = source.changesSince(saved)
                override suspend fun applyChanges(changes: ByteArray) = source.applyChanges(changes)
            }
        }
        merchantsDocumentId(homeId) -> merchants?.forHome(homeId)?.let { source ->
            object : MailboxDocument {
                override suspend fun heads() = source.heads()
                override suspend fun changesSince(saved: ByteArray) = source.changesSince(saved)
                override suspend fun applyChanges(changes: ByteArray) = source.applyChanges(changes)
            }
        }
        else -> null
    }
}
