package uk.veritaslabs.vesta.location

/** What loading the locality pack came to. */
sealed interface LocalityPackState {
    /** Neither a pointer nor a root address is configured. */
    data object Unconfigured : LocalityPackState

    /** This platform has no way to fetch chunks, and nothing is cached. */
    data object NotAvailableOnDevice : LocalityPackState

    data class Unreachable(val reason: String) : LocalityPackState

    data class Ready(val root: LocalityRoot, val pack: LocalityPack) : LocalityPackState
}

/** Where the locality pack comes from. */
interface LocalityPackSource {
    /** True when [load] can finish without touching the network. */
    suspend fun isCachedLocally(): Boolean

    /**
     * A check that never touches the network: [LocalityPackState.Unconfigured] or [LocalityPackState.NotAvailableOnDevice]
     * when no load could succeed, null when the flow can proceed.
     */
    suspend fun preflight(): LocalityPackState?

    /** Never throws; a failure is a state. */
    suspend fun load(): LocalityPackState
}
