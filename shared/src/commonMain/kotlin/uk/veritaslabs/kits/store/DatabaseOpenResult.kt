package uk.veritaslabs.kits.store

/** The outcome of opening the local database at launch, for the app to show a message instead of crashing. */
sealed interface DatabaseOpenResult {
    data class Opened(val database: KitsDatabase) : DatabaseOpenResult

    /** The file is from a newer Vesta; it was left untouched. See [DatabaseDowngradeException]. */
    data class TooNew(val error: DatabaseDowngradeException) : DatabaseOpenResult
}

/** Like [createVestaDatabase], but a database from a newer version comes back as [DatabaseOpenResult.TooNew]. */
fun openVestaDatabase(driverFactory: DatabaseDriverFactory): DatabaseOpenResult =
    try {
        DatabaseOpenResult.Opened(createVestaDatabase(driverFactory))
    } catch (e: DatabaseDowngradeException) {
        DatabaseOpenResult.TooNew(e)
    }
