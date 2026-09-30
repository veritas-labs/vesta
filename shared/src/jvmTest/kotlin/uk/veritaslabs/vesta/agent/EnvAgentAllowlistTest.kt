package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvAgentAllowlistTest {

    @Test
    fun allowsTheCommaSeparatedIdsTrimmed() {
        // Arrange
        val allowlist = EnvAgentAllowlist { " abc , def,, " }

        // Act / Assert
        assertTrue(allowlist.allows("abc"))
        assertTrue(allowlist.allows("def"))
        assertFalse(allowlist.allows("ghi"))
        assertFalse(allowlist.allows(""))
    }

    @Test
    fun unsetOrEmptyAllowsNothing() {
        // Arrange
        val unset = EnvAgentAllowlist { null }
        val empty = EnvAgentAllowlist { "" }

        // Act / Assert
        assertFalse(unset.allows("abc"))
        assertFalse(empty.allows("abc"))
    }
}
