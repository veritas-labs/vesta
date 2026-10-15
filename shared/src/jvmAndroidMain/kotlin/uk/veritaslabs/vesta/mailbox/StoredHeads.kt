package uk.veritaslabs.vesta.mailbox

import org.automerge.ChangeHash
import org.automerge.Document

// The implementations live in `docs`, which may not depend on feature packages; these keep the mailbox's names.

internal fun encodeHeads(heads: Array<ChangeHash>): ByteArray = uk.veritaslabs.vesta.docs.encodeHeads(heads)

internal fun decodeHeads(bytes: ByteArray): Array<ChangeHash> = uk.veritaslabs.vesta.docs.decodeHeads(bytes)

/** The changes made since [saved] heads (everything, for none), encoded for [applyEncodedChanges][Document.applyEncodedChanges]. */
internal fun Document.changesSince(saved: ByteArray): ByteArray = encodeChangesSince(decodeHeads(saved))
