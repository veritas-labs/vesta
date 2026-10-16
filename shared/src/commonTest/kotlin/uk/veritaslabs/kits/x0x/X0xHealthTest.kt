package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X0xHealthTest {

    @Test
    fun parsesRealHealthBody() {
        val health = X0xHealth.parse(
            """{"ok":true,"status":"healthy","version":"0.40.4","peers":8,"send_ready_peers":8,"uptime_secs":7}"""
        )
        assertTrue(health != null)
        assertTrue(health.ok)
        assertEquals("healthy", health.status)
        assertEquals("0.40.4", health.version)
        assertEquals(8, health.peers)
        assertEquals(8, health.sendReadyPeers)
        assertEquals(7, health.uptimeSecs)
    }

    @Test
    fun ignoresUnknownKeysAndDefaultsMissingOnes() {
        val health = X0xHealth.parse("""{"ok":false,"status":"degraded","extra":"ignored"}""")
        assertTrue(health != null)
        assertFalse(health.ok)
        assertEquals("degraded", health.status)
        assertEquals("?", health.version)
        assertEquals(0, health.peers)
        assertEquals(0, health.sendReadyPeers)
        assertEquals(0, health.uptimeSecs)
    }

    @Test
    fun returnsNullForUnrecognisableBodies() {
        assertNull(X0xHealth.parse("not json"))
        assertNull(X0xHealth.parse("""{"status":"healthy"}"""))
        assertNull(X0xHealth.parse("403 Forbidden"))
    }
}
