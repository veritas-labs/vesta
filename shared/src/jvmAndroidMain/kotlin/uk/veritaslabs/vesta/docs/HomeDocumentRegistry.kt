package uk.veritaslabs.vesta.docs

/**
 * The single list of Home document types. The sync engine, mailbox, acknowledgement and activity log find a document
 * through it instead of each keeping their own list, which is how a type used to go missing from one of them.
 *
 * Ids are `"$homeId:$feature"` (ADR-006). They are parsed on the LAST `:`, which is safe because Home ids are random
 * UUIDs and never contain one, and feature keys containing `:` are rejected here.
 *
 * @param types in registration order, which is the order [documentIds] returns.
 */
internal class HomeDocumentRegistry(val types: List<HomeDocumentType>) {
    private val byFeature: Map<String, HomeDocumentType>

    init {
        types.forEach { require(':' !in it.feature) { "Feature key '${it.feature}' must not contain ':'" } }
        val duplicates = types.groupBy { it.feature }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate feature keys: $duplicates" }
        byFeature = types.associateBy { it.feature }
    }

    /** A document id split into its Home and its type. */
    data class Parsed(val homeId: String, val type: HomeDocumentType)

    /** Splits [documentId] on its last `:`; null when it has none or the feature is not registered. */
    fun parse(documentId: String): Parsed? {
        val split = documentId.lastIndexOf(':')
        if (split < 0) return null
        return byFeature[documentId.substring(split + 1)]?.let { Parsed(documentId.substring(0, split), it) }
    }

    /** The type registered under [feature], or null. */
    fun type(feature: String): HomeDocumentType? = byFeature[feature]

    /** The ids of the Home's documents, in registration order, optionally only those of one [scope]. */
    fun documentIds(homeId: String, scope: Scope? = null): List<String> =
        types.filter { scope == null || it.scope == scope }.map { "$homeId:${it.feature}" }

    /** Opens [documentId] of the Home [homeId], or null when it is not a registered type's id or the document is absent. */
    suspend fun open(homeId: String, documentId: String): HomeDocument? =
        parse(documentId)?.takeIf { it.homeId == homeId }?.type?.open?.invoke(homeId)

    /** The Sync activity module of [documentId], or null when it is not a registered Home document. */
    fun moduleOf(documentId: String): String? = parse(documentId)?.type?.module
}
