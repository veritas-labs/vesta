package uk.veritaslabs.kits.sync

/** Why sync is not working. */
enum class ProblemReason { DaemonUnreachable, MessageStalled }

/** The status card's one-line answer to "is my Space in sync?" (docs/design/sync-activity.md §6). */
sealed interface SyncVerdict {
    data class Problem(val reason: ProblemReason) : SyncVerdict
    data object NeedsNewerVersion : SyncVerdict
    data object OnlyYou : SyncVerdict
    data class UpToDate(val withName: String?, val msAgo: Long) : SyncVerdict
    data object WaitingForOthers : SyncVerdict
    data object ChangesWaiting : SyncVerdict
    data object Syncing : SyncVerdict

    companion object {
        /** Two sync rounds (30 s each): how recently a sync still counts as "up to date". */
        const val UP_TO_DATE_WINDOW_MS = 60_000L

        /** A membership message failing for longer than this is a problem. */
        const val STALLED_MESSAGE_MS = 60 * 60 * 1000L

        /**
         * Derives the verdict from [snapshot] as of [now] (epoch millis). A round in progress shows [Syncing] while it runs;
         * otherwise the first match wins: [Problem], [NeedsNewerVersion], [OnlyYou], [UpToDate], [ChangesWaiting], [WaitingForOthers].
         * [ChangesWaiting] sits before [WaitingForOthers] because the latter means nothing is left unsent.
         */
        fun of(snapshot: SyncSnapshot, now: Long): SyncVerdict {
            if (snapshot.roundInProgress) return Syncing
            if (!snapshot.daemonReachable) return Problem(ProblemReason.DaemonUnreachable)
            snapshot.stalledMessageAgeMs?.takeIf { it > STALLED_MESSAGE_MS }?.let { return Problem(ProblemReason.MessageStalled) }
            if (snapshot.needsNewerVersion) return NeedsNewerVersion
            if (!snapshot.hasOtherDevices) return OnlyYou
            val sinceSync = snapshot.lastSyncedMsAgo?.let { it + (now - snapshot.takenAt).coerceAtLeast(0) }
            if (!snapshot.lastRoundHadFailures && sinceSync != null && sinceSync <= UP_TO_DATE_WINDOW_MS) {
                return UpToDate(snapshot.lastSyncedPeerName, sinceSync)
            }
            if (snapshot.changesWaiting) return ChangesWaiting
            return WaitingForOthers
        }
    }
}
