package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.blobs.BlobReference
import uk.veritaslabs.kits.blobs.BlobReferenceSource

/** Vesta's blob references are the photos in each Home's cookbook (ADR-022, ADR-023). */
class CookbookBlobReferences(private val cookbooks: CookbookSources) : BlobReferenceSource {

    override val localChanges: Flow<String> get() = cookbooks.localChanges

    override suspend fun references(homeId: String): Set<BlobReference>? = cookbooks.forHome(homeId)?.blobReferences()

    override suspend fun watch(homeId: String, onChange: suspend () -> Unit) {
        cookbooks.forHome(homeId)?.recipes?.collect { onChange() }
    }
}

/** The ids of every Home on this device, for the blob service, which has no Home vocabulary of its own. */
fun homeIdsOf(database: KitsDatabase): suspend () -> List<String> = { database.homeQueries.selectAll().awaitAsList().map { it.id } }
