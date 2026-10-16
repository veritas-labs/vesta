package uk.veritaslabs.vesta

import uk.veritaslabs.kits.store.DatabaseLocation

/**
 * Where Vesta keeps its database: `~/.vesta/vesta.db` on the desktop, `getDatabasePath("vesta.db")` on Android and
 * `<documents>/vesta.db` on iOS. Existing installs hold their data there, so these two strings never change.
 */
val VestaDatabaseLocation = DatabaseLocation(appDirName = ".vesta", fileName = "vesta.db")
