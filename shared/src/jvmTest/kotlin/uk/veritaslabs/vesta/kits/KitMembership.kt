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
    /** Extracted in WP14: consumed as the published `uk.veritaslabs.kits:x0x-kit`, no source of it lives in `:shared`. */
    X0x("x0x"),
    /** Extracted in WP15: consumed as the published `uk.veritaslabs.kits:store-kit` (the schema lives there). */
    Store("store"),
    /** Extracted in WP20: consumed as the published `uk.veritaslabs.kits:docs-kit` (JVM and Android only). */
    Docs("docs"),
    /** Extracted in WP22: consumed as the published `uk.veritaslabs.kits:spaces-kit` (common part with iOS, services JVM and Android). */
    Spaces("spaces"),
    Sync("sync"),
    /** Extracted in WP18: consumed as the published `uk.veritaslabs.kits:blobs-kit`. */
    Blobs("blobs"),
    Assistants("assistants"),
    Vesta("vesta"),
}

object KitMembership {
    /** Kits that have been extracted: `:shared` imports them but must not contain their sources. */
    val extracted: Set<Kit> = setOf(Kit.X0x, Kit.Store, Kit.Blobs, Kit.Docs, Kit.Spaces)

    /** The package each extracted kit publishes, so a stray copy of its sources is noticed. */
    val extractedPackages: Map<Kit, String> = mapOf(
        Kit.X0x to "uk.veritaslabs.kits.x0x",
        Kit.Store to "uk.veritaslabs.kits.store",
        Kit.Blobs to "uk.veritaslabs.kits.blobs",
        Kit.Docs to "uk.veritaslabs.kits.docs",
        Kit.Spaces to "uk.veritaslabs.kits.spaces",
    )

    private val platformSuffix = Regex("\\.(android|jvm|ios)$")

    /** Whole directories that belong to one kit, with the files that stay behind in Vesta. */
    private val directories: Map<String, Pair<Kit, Set<String>>> = mapOf(
        "blobs/" to (Kit.Blobs to emptySet()),
    )

    /** Individual files of directories that are split between kits. */
    private val files: Map<Kit, Set<String>> = mapOf(
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
        Kit.Assistants to 5,
    )
}
