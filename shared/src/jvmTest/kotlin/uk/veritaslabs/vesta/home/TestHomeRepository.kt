package uk.veritaslabs.vesta.home

import kotlin.time.Clock
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.getDeviceName
import uk.veritaslabs.vesta.profile.ProfileRepository

/** A [HomeRepository] over [database] whose Profile is named after this machine, as the apps do. */
fun homeRepository(
    database: KitsDatabase,
    changes: HomeChangeListener? = null,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): HomeRepository = HomeRepository(
    database, changes, ProfileRepository(database, deviceName = ::getDeviceName), nowMillis = nowMillis,
)
