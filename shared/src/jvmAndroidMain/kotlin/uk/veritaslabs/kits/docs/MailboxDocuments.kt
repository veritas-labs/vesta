package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi


/** The documents a Space's mailbox carries. */
@KitsInternalApi
public interface MailboxDocuments {
    /** The ids of [spaceId]'s documents, as the mailbox names them in its entries. */
    fun documentIds(spaceId: String): List<String>

    /** [documentId] of [spaceId], or null when this device has no such Space or this version doesn't know the document. */
    suspend fun open(spaceId: String, documentId: String): SpaceDocument?
}

/** The shared documents of a Space's registry (every type but the private ones), in registration order. */
@KitsInternalApi
public class SpaceMailboxDocuments(private val registry: SpaceDocumentRegistry) : MailboxDocuments {
    override fun documentIds(spaceId: String) = registry.documentIds(spaceId, Scope.Shared)

    override suspend fun open(spaceId: String, documentId: String) = registry.open(spaceId, documentId)
}
