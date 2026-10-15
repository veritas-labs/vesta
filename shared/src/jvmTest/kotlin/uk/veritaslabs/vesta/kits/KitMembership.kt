package uk.veritaslabs.vesta.kits

/**
 * Which kit each main-source file of `:shared` belongs to once the kits are extracted
 * (docs/plans/kits-extraction-implementation-plan.md, Appendix A). Paths are relative to
 * `uk/veritaslabs/vesta/`, written without the `.kt` extension and without the platform suffix
 * (`.android`, `.jvm`, `.ios`), so `db/DatabaseDriverFactory` covers all four of its files.
 *
 * Anything not listed here belongs to [Kit.Vesta]: the app itself.
 */
enum class Kit(val label: String) {
    X0x("x0x"),
    Store("store"),
    Docs("docs"),
    Spaces("spaces"),
    Sync("sync"),
    Blobs("blobs"),
    Assistants("assistants"),
    Vesta("vesta"),
}

object KitMembership {
    private val platformSuffix = Regex("\\.(android|jvm|ios)$")

    /** Whole directories that belong to one kit, with the files that stay behind in Vesta. */
    private val directories: Map<String, Pair<Kit, Set<String>>> = mapOf(
        "x0x/" to (Kit.X0x to setOf("x0x/X0xStatusCard", "x0x/X0xConnectivityIndicator")),
        "db/" to (Kit.Store to emptySet()),
        "docs/" to (Kit.Docs to emptySet()),
        "mailbox/" to (Kit.Docs to emptySet()),
        "profile/" to (Kit.Spaces to emptySet()),
        "devicesync/" to (Kit.Sync to emptySet()),
        "blobs/" to (Kit.Blobs to setOf("blobs/Placeholder")),
    )

    /** Individual files of directories that are split between kits. */
    private val files: Map<Kit, Set<String>> = mapOf(
        Kit.Docs to setOf(
            "datasync/SyncEnvelope", "datasync/SyncableDocument", "datasync/SyncAcknowledgement",
            "datasync/SyncEventRecorder", "datasync/ChangesWaiting",
        ),
        Kit.Spaces to setOf(
            "datasync/HomeRosters", "datasync/HomeRegistry", "datasync/HomeJoinService",
            "datasync/HomeGroups", "datasync/HomeHubs", "datasync/ProfileRegistry", "datasync/HomeSeeding",
            "automerge/HomeRosterDocument", "automerge/HomeRegistryDocument",
            "automerge/InviteSecrets", "automerge/ProfileRegistryDocument",
            "home/HomeRepository", "home/MemberRepository", "home/HomeChangeListener",
            "home/RosterChangeListener", "home/MemberRole", "home/HomeListItem", "home/HomeDetails", "home/HomeConfig",
            "homejoin/HomeJoining", "homejoin/HomeJoinMessage", "homejoin/InviteLink",
            "homegroup/HomeHub", "homegroup/HomeGroupMessage",
        ),
        Kit.Sync to setOf(
            "datasync/DeviceDataSyncService", "datasync/SyncActivityService",
            "devicepairing/PairingLink",
            "syncactivity/SyncActivityModels", "syncactivity/SyncActivityRepository",
            "syncactivity/SyncVerdict",
        ),
        Kit.Assistants to setOf(
            "agent/AgentAllowlist", "agent/AssistantRepository", "agent/Assistants",
            "agent/CommandLog", "agent/EnvAgentAllowlist",
        ),
    )

    /** The path as this table spells it: relative to the vesta package, no extension, no platform suffix. */
    fun key(pathUnderVesta: String): String =
        pathUnderVesta.removeSuffix(".kt").replace(platformSuffix, "")

    fun kitOf(pathUnderVesta: String): Kit {
        val key = key(pathUnderVesta)
        files.entries.firstOrNull { key in it.value }?.let { return it.key }
        directories.entries.firstOrNull { key.startsWith(it.key) }?.let { (_, entry) ->
            val (kit, staysInVesta) = entry
            return if (key in staysInVesta) Kit.Vesta else kit
        }
        return Kit.Vesta
    }

    /** The kits each kit may import from. Vesta may import any kit; no kit may import Vesta. */
    val allowedImports: Map<Kit, Set<Kit>> = mapOf(
        Kit.X0x to emptySet(),
        Kit.Store to emptySet(),
        Kit.Docs to setOf(Kit.X0x, Kit.Store),
        Kit.Spaces to setOf(Kit.Docs, Kit.X0x, Kit.Store),
        Kit.Sync to setOf(Kit.Docs, Kit.Spaces, Kit.X0x, Kit.Store),
        Kit.Blobs to setOf(Kit.X0x, Kit.Store),
        Kit.Assistants to setOf(Kit.Store),
        Kit.Vesta to Kit.entries.toSet(),
    )

    /** File counts from Appendix A, so a rename that silently drops a file out of a kit is noticed. */
    val expectedFileCounts: Map<Kit, Int> = mapOf(
        Kit.X0x to 15,
        Kit.Store to 9,
        Kit.Docs to 17,
        Kit.Spaces to 26, // Appendix A's 21 plus MemberRole, HomeListItem, HomeDetails (WP1), HomeConfig (WP3) and HomeSeeding (WP4)
        Kit.Sync to 13, // plus SyncedDevice (WP1) and DeviceSyncConfig (WP3)
        Kit.Blobs to 16, // plus MediaKind (WP1)
        Kit.Assistants to 5,
    )
}
