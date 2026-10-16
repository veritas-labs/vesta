package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi

import org.automerge.ChangeHash
import org.automerge.Document

/** An Automerge change hash rebuilt from saved bytes: the library only hands them out, and its constructor is protected. */
private class StoredChangeHash(bytes: ByteArray) : ChangeHash(bytes)

/** Every hash is a SHA-256, so a saved list of heads is the hashes laid end to end. */
private const val HashSize = 32

@KitsInternalApi public fun encodeHeads(heads: Array<ChangeHash>): ByteArray = heads.map { it.bytes }.fold(ByteArray(0)) { all, hash -> all + hash }

@KitsInternalApi public fun decodeHeads(bytes: ByteArray): Array<ChangeHash> {
    require(bytes.size % HashSize == 0) { "saved heads must be a whole number of $HashSize-byte hashes" }
    return Array(bytes.size / HashSize) { StoredChangeHash(bytes.copyOfRange(it * HashSize, (it + 1) * HashSize)) }
}

/** The changes made since [saved] heads (everything, for none), encoded for [applyEncodedChanges][Document.applyEncodedChanges]. */
@KitsInternalApi public fun Document.changesSince(saved: ByteArray): ByteArray = encodeChangesSince(decodeHeads(saved))
