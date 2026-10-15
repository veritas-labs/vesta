package uk.veritaslabs.vesta.syncactivity

/** What a log entry records. Content-free: only the transport fact (docs/design/sync-activity.md §8.2). */
enum class SyncEventKind { Received, Sent, Failed, Membership, MailboxIn, MailboxOut, UnsupportedVersion, UnknownMessage }

/** The Home document a log entry is about, named plainly. */
enum class SyncModuleName { Pantry, Shopping, Cookbook, MealPlan, Calendar, Tasks, Merchants, Members, Mailbox }

/** Which log entries the filter menu shows. */
enum class SyncEventFilter { All, Received, Sent, Problems }

/**
 * One row of the Sync activity log. Repeats of the same (module, peer, direction) within the collapse window arrive already
 * folded into one row: [at] is the first, [lastAt] the latest, and [count] how many there were. [peerAgent] is the counterparty's
 * agent id (null when there is none); [detail] is a short code such as `unreachable` or `retrying`, localised by the UI; for an unsupported version or unknown message it
 * is the version (`v2`), document type or message kind, shown as it is.
 */
data class SyncEventItem(
    val id: Long,
    val at: Long,
    val lastAt: Long,
    val kind: SyncEventKind,
    val module: SyncModuleName,
    val peerAgent: String? = null,
    val detail: String? = null,
    val count: Int = 1,
)

/**
 * One device or hub of the Home as the screen lists it. [ownerName] is null for the user's own devices ([isYou] marks this one),
 * [lastSyncedMsAgo] is null when the two have never synced, and [retryingSinceMsAgo] is the age of an outbox message still
 * waiting to reach it (null when there is none).
 */
data class PeerActivity(
    val agentId: String,
    val name: String,
    val ownerName: String? = null,
    val isYou: Boolean = false,
    val isHub: Boolean = false,
    val isOwnDevice: Boolean = false,
    val online: Boolean = false,
    val lastSyncedMsAgo: Long? = null,
    val retryingSinceMsAgo: Long? = null,
)

/**
 * The raw facts the verdict is derived from, taken at [takenAt] (epoch millis; the "ms ago" figures are relative to it).
 * [peers] includes the hubs; [hasOtherDevices] is false when the Home's roster lists only this device and no hub.
 * [stalledMessageAgeMs] is the age of the oldest pending membership message that keeps failing, or null when none does.
 * [needsNewerVersion] is true when a device sent changes in a format this version cannot read in the last week (ADR-034).
 * [lastSyncedPeerName] and [lastSyncedMsAgo] name the most recent successful sync, for the "Up to date" sentence.
 */
data class SyncSnapshot(
    val takenAt: Long,
    val peers: List<PeerActivity> = emptyList(),
    val hasOtherDevices: Boolean = false,
    val daemonReachable: Boolean = true,
    val roundInProgress: Boolean = false,
    val lastRoundAt: Long? = null,
    val lastRoundHadFailures: Boolean = false,
    val changesWaiting: Boolean = false,
    val stalledMessageAgeMs: Long? = null,
    val needsNewerVersion: Boolean = false,
    val lastSyncedPeerName: String? = null,
    val lastSyncedMsAgo: Long? = null,
)
