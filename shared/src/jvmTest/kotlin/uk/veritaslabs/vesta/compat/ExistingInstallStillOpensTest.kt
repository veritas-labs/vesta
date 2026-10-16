package uk.veritaslabs.vesta.compat

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.kits.spaces.SpaceRegistryDocumentId
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.kits.spaces.SpaceRosters
import uk.veritaslabs.vesta.datasync.TestHomeDocuments
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.kits.spaces.SpaceJoinMessage
import uk.veritaslabs.kits.spaces.ProfileRepository

/**
 * A copy of a version-20 database written by the code before the Kits extraction (`vesta-v20.db`, produced once by
 * [WriteExistingInstallFixture] and never regenerated) must still open with the code under test, through the same open
 * path the app uses, and give back all its data (docs/plans/kits-extraction-implementation-plan.md, WP10). After a run,
 * `git diff --exit-code shared/src/jvmTest/resources/compat/vesta-v20.db` must report no change.
 */
class ExistingInstallStillOpensTest {

    private fun fixtureCopy(): File {
        val source = checkNotNull(javaClass.getResourceAsStream("/compat/vesta-v20.db")) { "fixture missing" }
        val copy = File.createTempFile("existing-install", ".db").apply { deleteOnExit() }
        source.use { input -> copy.outputStream().use { input.copyTo(it) } }
        return copy
    }

    private fun userVersion(file: File): Long = BundledSQLiteDriver().open(file.path).let { connection ->
        try {
            connection.prepare("PRAGMA user_version").let { s -> try { s.step(); s.getLong(0) } finally { s.close() } }
        } finally {
            connection.close()
        }
    }

    @Test
    fun theFixtureIsAVersionTwentyDatabaseWithARowInEveryTable() {
        // Arrange
        val file = fixtureCopy()

        // Act
        val version = userVersion(file)
        val emptyTables = BundledSQLiteDriver().open(file.path).let { connection ->
            try {
                val tables = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").let { s ->
                    try { buildList { while (s.step()) add(s.getText(0)) } } finally { s.close() }
                }
                tables.filter { table ->
                    connection.prepare("SELECT count(*) FROM \"$table\"").let { s -> try { s.step(); s.getLong(0) == 0L } finally { s.close() } }
                }
            } finally {
                connection.close()
            }
        }

        // Assert
        assertEquals(20L, version)
        assertEquals(emptyList(), emptyTables)
    }

    @Test
    fun theOldDatabaseOpensThroughTheAppsOpenPathAtVersionTwentyWithItsHomeAndMembers() = runBlocking {
        // Arrange
        val file = fixtureCopy()

        // Act
        val (database, driver) = openAsTheAppDoes(file)
        val homes = database.homeQueries.selectAll().awaitAsList()
        val members = database.homeMemberQueries.selectByHome(homes.single().id).awaitAsList()
        driver.close()

        // Assert
        assertEquals(20L, KitsDatabase.Schema.version)
        assertEquals(20L, userVersion(file))
        assertEquals(listOf("The Johnsons"), homes.map { it.name })
        assertEquals("family", homes.single().shape)
        assertEquals(setOf("Sam"), members.map { it.name }.filter { it == "Sam" }.toSet())
    }

    @Test
    fun everyOneOfTheTenDocumentsLoadsThroughTheRegistryAndIsAlreadyStored() = runBlocking {
        // Arrange
        val (database, driver) = openAsTheAppDoes(fixtureCopy())
        val homeId = database.homeQueries.selectAll().awaitAsList().single().id
        val profiles = ProfileRepository(database, deviceName = { "Mark's desktop" })
        val rosters = SpaceRosters(database, profiles, deviceName = { "Mark's desktop" })
        val registry = TestHomeDocuments(database, rosters, publishRosters = {}, sources = VestaHomeSources(database)).registry

        // Act
        val ids = registry.documentIds(homeId)
        val opened = ids.map { registry.open(homeId, it) }
        val stored = database.automergeDocumentQueries.selectByHome(homeId).awaitAsList().map { it.documentId }.toSet()
        val userDocument = database.userDocumentQueries.selectById(SpaceRegistryDocumentId).awaitAsList()
        driver.close()

        // Assert
        assertEquals(10, ids.size)
        assertTrue(opened.all { it != null }, "documents that did not open: ${ids.zip(opened).filter { it.second == null }}")
        assertTrue(ids.all { it in stored }, "ids not stored by the old build: ${ids.filter { it !in stored }}")
        assertEquals(1, userDocument.size)
    }

    @Test
    fun theRosterStillHasItsMembersDeviceTombstoneInviteHubGroupAndGoneDevice() = runBlocking {
        // Arrange
        val (database, driver) = openAsTheAppDoes(fixtureCopy())
        val homeId = database.homeQueries.selectAll().awaitAsList().single().id
        val profiles = ProfileRepository(database, deviceName = { "Mark's desktop" })
        val rosters = SpaceRosters(database, profiles, deviceName = { "Mark's desktop" })

        // Act
        val members = rosters.members(homeId).associateBy { it.memberId }
        val invites = rosters.invites(homeId)
        val hubs = rosters.hubs(homeId)
        val group = rosters.groupId(homeId)
        val devices = rosters.devices(homeId)
        driver.close()

        // Assert
        assertEquals(3, members.size)
        assertEquals("Sam", members.getValue("member-2").name)
        assertEquals(setOf("agent-2"), members.getValue("member-2").devices)
        assertTrue(members.getValue("member-3").removed, "the tombstone")
        assertEquals(1, invites.size)
        assertEquals(2, invites.single().maxUses)
        assertEquals(setOf("hub-agent"), hubs)
        assertEquals("group-1", group)
        assertTrue("agent-1" in devices.values.flatten())
    }

    @Test
    fun thePendingMessageStillDecodesAsAHomeJoinLeftAndTheMailboxRowIsThere() = runBlocking {
        // Arrange
        val (database, driver) = openAsTheAppDoes(fixtureCopy())
        val homeId = database.homeQueries.selectAll().awaitAsList().single().id

        // Act
        val pending = database.pendingMessageQueries.selectAll().awaitAsList().single()
        val decoded = SpaceJoinMessage.decode(Base64.Default.encode(pending.payload))
        val heads = database.mailboxQueries.selectHeads(homeId, "$homeId:pantry").awaitAsList()
        driver.close()

        // Assert
        assertEquals(SpaceJoinMessage.LeftKind, pending.kind)
        val left = assertNotNull(decoded as? SpaceJoinMessage.Left)
        assertEquals(homeId, left.spaceId)
        assertEquals(1, heads.size)
    }
}
