package uk.veritaslabs.kits.docs

/**
 * The single list of Space document types. The sync engine, mailbox, acknowledgement and activity log find a document
 * through it instead of each keeping their own list, which is how a type used to go missing from one of them.
 *
 * Ids are `"$spaceId:$feature"` (ADR-006). They are parsed on the LAST `:`, which is safe because Space ids are random
 * UUIDs and never contain one, and feature keys containing `:` are rejected here.
 *
 * @param types in registration order, which is the order [documentIds] returns.
 */
public class SpaceDocumentRegistry(val types: List<SpaceDocumentType>) {
    private val byFeature: Map<String, SpaceDocumentType>

    init {
        types.forEach { require(':' !in it.feature) { "Feature key '${it.feature}' must not contain ':'" } }
        val duplicates = types.groupBy { it.feature }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate feature keys: $duplicates" }
        byFeature = types.associateBy { it.feature }
    }

    /** A document id split into its Space and its type. */
    data class Parsed(val spaceId: String, val type: SpaceDocumentType)

    /** Splits [documentId] on its last `:`; null when it has none or the feature is not registered. */
    fun parse(documentId: String): Parsed? {
        val split = documentId.lastIndexOf(':')
        if (split < 0) return null
        return byFeature[documentId.substring(split + 1)]?.let { Parsed(documentId.substring(0, split), it) }
    }

    /** The type registered under [feature], or null. */
    fun type(feature: String): SpaceDocumentType? = byFeature[feature]

    /** The ids of the Space's documents, in registration order, optionally only those of one [scope]. */
    fun documentIds(spaceId: String, scope: Scope? = null): List<String> =
        types.filter { scope == null || it.scope == scope }.map { it.documentId(spaceId) }

    /** Opens [documentId] of the Space [spaceId], or null when it is not a registered type's id or the document is absent. */
    suspend fun open(spaceId: String, documentId: String): SpaceDocument? =
        parse(documentId)?.takeIf { it.spaceId == spaceId }?.type?.open?.invoke(spaceId)

    /** The Sync activity module of [documentId], or null when it is not a registered Space document. */
    fun moduleOf(documentId: String): String? = parse(documentId)?.type?.module
}
