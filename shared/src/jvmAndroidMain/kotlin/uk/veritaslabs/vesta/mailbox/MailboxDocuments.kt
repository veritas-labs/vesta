package uk.veritaslabs.vesta.mailbox

import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.docs.HomeDocumentRegistry
import uk.veritaslabs.vesta.docs.Scope

/** The documents a Home's mailbox carries. */
internal interface MailboxDocuments {
    /** The ids of [homeId]'s documents, as the mailbox names them in its entries. */
    fun documentIds(homeId: String): List<String>

    /** [documentId] of [homeId], or null when this device has no such Home or this version doesn't know the document. */
    suspend fun open(homeId: String, documentId: String): HomeDocument?
}

/** The shared documents of a Home's registry (every type but the private ones), in registration order. */
internal class HomeMailboxDocuments(private val registry: HomeDocumentRegistry) : MailboxDocuments {
    override fun documentIds(homeId: String) = registry.documentIds(homeId, Scope.Shared)

    override suspend fun open(homeId: String, documentId: String) = registry.open(homeId, documentId)
}
