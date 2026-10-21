package uk.veritaslabs.vesta.location

/** For platforms that cannot fetch the locality pack yet (Android, iOS): the area flow says it is not available on this device. */
object UnavailableLocalityPackSource : LocalityPackSource {
    override suspend fun isCachedLocally() = false

    override suspend fun preflight(): LocalityPackState? = LocalityPackState.NotAvailableOnDevice

    override suspend fun load(): LocalityPackState = LocalityPackState.NotAvailableOnDevice
}
