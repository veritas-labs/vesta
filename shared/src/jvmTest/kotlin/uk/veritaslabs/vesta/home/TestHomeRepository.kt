package uk.veritaslabs.vesta.home

import kotlin.time.Clock
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.getDeviceName
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.kits.spaces.SpaceRepository

import uk.veritaslabs.kits.spaces.SpaceChangeListener
import uk.veritaslabs.vesta.home.HomeRepository
/** A [HomeRepository] over [database] whose Profile is named after this machine, as the apps do. */
fun homeRepository(
    database: KitsDatabase,
    changes: SpaceChangeListener? = null,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): HomeRepository = HomeRepository(SpaceRepository(
    database, changes, ProfileRepository(database, deviceName = ::getDeviceName), nowMillis = nowMillis,
))
