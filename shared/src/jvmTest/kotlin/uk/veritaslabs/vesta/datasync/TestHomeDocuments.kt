package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentRegistry
import uk.veritaslabs.vesta.docs.HomeDocumentType

/**
 * The registry the engine builds (the roster it registers itself, then the app's types), for tests that need the
 * registry without the engine.
 */
internal class TestHomeDocuments(
    database: KitsDatabase,
    val rosters: HomeRosters,
    publishRosters: suspend () -> Unit,
    sources: VestaHomeSources = VestaHomeSources(database),
    extraTypes: List<HomeDocumentType> = emptyList(),
) {
    val registry = HomeDocumentRegistry(listOf(rosterDocumentType(rosters, publishRosters)) + sources.documentTypes() + extraTypes)
}
