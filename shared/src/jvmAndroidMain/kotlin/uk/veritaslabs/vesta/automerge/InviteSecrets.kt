package uk.veritaslabs.vesta.automerge

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

private const val SecretBytes = 16

/** A new random invite secret (docs/adr/ADR-018-invite-links.md), URL-safe so it can sit in a link. */
fun newInviteSecret(): String = Base64.UrlSafe.encode(ByteArray(SecretBytes).also { SecureRandom().nextBytes(it) })

/**
 * What the Home's roster stores in place of an invite's secret: its SHA-256 as lower-case hex. The roster is readable by
 * every member, so it must not hold the secret itself.
 */
fun inviteSecretHash(secret: String): String =
    MessageDigest.getInstance("SHA-256").digest(secret.encodeToByteArray()).joinToString("") { "%02x".format(it) }
