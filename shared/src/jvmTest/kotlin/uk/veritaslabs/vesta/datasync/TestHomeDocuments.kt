@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.kits.sync.rosterDocumentType
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.docs.SpaceDocumentRegistry
import uk.veritaslabs.kits.docs.SpaceDocumentType

import uk.veritaslabs.kits.spaces.SpaceRosters
/**
 * The registry the engine builds (the roster it registers itself, then the app's types), for tests that need the
 * registry without the engine.
 */
internal class TestHomeDocuments(
    database: KitsDatabase,
    val rosters: SpaceRosters,
    publishRosters: suspend () -> Unit,
    sources: VestaHomeSources = VestaHomeSources(database),
    extraTypes: List<SpaceDocumentType> = emptyList(),
) {
    val registry = SpaceDocumentRegistry(listOf(rosterDocumentType(rosters, publishRosters)) + sources.documentTypes() + extraTypes)
}
