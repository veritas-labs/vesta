package uk.veritaslabs.vesta.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class VestaDatabaseTest {

    @Test
    fun homeMembershipJoinsAcrossTables() = runTest {
        val db = openInMemoryDatabase()
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.homeMemberQueries.insert("home-1", "profile-1", "Arthur", null, "admin", 1_000L)

        val homes = db.homeQueries.selectForMember("profile-1").awaitAsList()
        assertEquals(listOf("The Johnsons"), homes.map { it.name })
        assertEquals(1L, db.homeMemberQueries.countAdmins("home-1").awaitAsOne())
    }

    @Test
    fun deletingHomeCascadesToMembers() = runTest {
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.homeMemberQueries.insert("home-1", "profile-1", "Arthur", null, "admin", 1_000L)

        db.homeQueries.deleteById("home-1")

        assertTrue(db.homeMemberQueries.selectByHome("home-1").awaitAsList().isEmpty())
        // The Profile itself is untouched — only the membership row is scoped to the Home.
        assertEquals(1, db.profileQueries.selectAll().awaitAsList().size)
    }

    @Test
    fun inviteCodeLookupHonoursExpiryAndRevocation() = runTest {
        val db = openInMemoryDatabase()
        db.homeQueries.insert("home-1", "The Johnsons", "family", 1_000L)
        db.inviteCodeQueries.insert("invite-1", "home-1", "482917", 1_000L, 2_000L)

        assertEquals(
            "invite-1",
            db.inviteCodeQueries.selectActiveByCode("482917", 1_500L).awaitAsOneOrNull()?.id,
        )
        assertNull(db.inviteCodeQueries.selectActiveByCode("482917", 2_500L).awaitAsOneOrNull())

        db.inviteCodeQueries.revoke("invite-1")
        assertNull(db.inviteCodeQueries.selectActiveByCode("482917", 1_500L).awaitAsOneOrNull())
    }

    @Test
    fun devicePairingLinkLookupHonoursExpiryAndConsumption() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.devicePairingLinkQueries.insert("pairing-1", "aa11bb22cc33dd44", 1_000L, 2_000L)

        // Act
        val activeBeforeExpiry = db.devicePairingLinkQueries.selectActive(1_500L).awaitAsOneOrNull()
        val activeAfterExpiry = db.devicePairingLinkQueries.selectActive(2_500L).awaitAsOneOrNull()

        // Assert
        assertEquals("pairing-1", activeBeforeExpiry?.id)
        assertNull(activeAfterExpiry)
    }

    @Test
    fun devicePairingLinkMarkConsumedExcludesItFromSelectActive() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.devicePairingLinkQueries.insert("pairing-1", "aa11bb22cc33dd44", 1_000L, 2_000L)

        // Act
        db.devicePairingLinkQueries.markConsumed("pairing-1")

        // Assert
        assertNull(db.devicePairingLinkQueries.selectActive(1_500L).awaitAsOneOrNull())
    }
}
