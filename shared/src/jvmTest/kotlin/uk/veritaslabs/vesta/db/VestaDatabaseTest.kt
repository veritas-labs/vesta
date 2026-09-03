package uk.veritaslabs.vesta.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VestaDatabaseTest {

    private fun openInMemoryDatabase(): VestaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
        VestaDatabase.Schema.create(driver)
        return VestaDatabase(driver)
    }

    @Test
    fun insertedProfileIsReadBack() {
        val db = openInMemoryDatabase()

        db.profileQueries.insert(
            id = "profile-1",
            displayName = "Arthur",
            avatarResourceId = null,
            x0xInstanceName = "default",
        )

        val profiles = db.profileQueries.selectAll().executeAsList()
        assertEquals(1, profiles.size)
        assertEquals("Arthur", profiles.single().displayName)
    }

    @Test
    fun homeMembershipJoinsAcrossTables() {
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.homeMemberQueries.insert("home-1", "profile-1", "admin", 1_000L)

        val homes = db.homeQueries.selectForProfile("profile-1").executeAsList()
        assertEquals(listOf("The Johnsons"), homes.map { it.name })
        assertEquals(1L, db.homeMemberQueries.countAdmins("home-1").executeAsOne())
    }

    @Test
    fun deletingHomeCascadesToMembers() {
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.homeMemberQueries.insert("home-1", "profile-1", "admin", 1_000L)

        db.homeQueries.deleteById("home-1")

        assertTrue(db.homeMemberQueries.selectByHome("home-1").executeAsList().isEmpty())
        // The Profile itself is untouched — only the membership row is scoped to the Home.
        assertEquals(1, db.profileQueries.selectAll().executeAsList().size)
    }

    @Test
    fun inviteCodeLookupHonoursExpiryAndRevocation() {
        val db = openInMemoryDatabase()
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.inviteCodeQueries.insert("invite-1", "home-1", "482917", 1_000L, 2_000L)

        assertEquals(
            "invite-1",
            db.inviteCodeQueries.selectActiveByCode("482917", 1_500L).executeAsOneOrNull()?.id,
        )
        assertNull(db.inviteCodeQueries.selectActiveByCode("482917", 2_500L).executeAsOneOrNull())

        db.inviteCodeQueries.revoke("invite-1")
        assertNull(db.inviteCodeQueries.selectActiveByCode("482917", 1_500L).executeAsOneOrNull())
    }
}
