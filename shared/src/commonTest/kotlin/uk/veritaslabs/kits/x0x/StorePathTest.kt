package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertEquals

class StorePathTest {

    @Test
    fun `a group store id and a writer-sequence key are each one path segment`() {
        // Arrange
        val storeId = "x0x/group/abc123/kv/def456"
        val key = "agent1/0000000042"

        // Act
        val path = storeValuePath(storeId, key)

        // Assert
        assertEquals("/stores/x0x%2Fgroup%2Fabc123%2Fkv%2Fdef456/agent1%2F0000000042", path)
    }

    @Test
    fun `plain store ids and keys are left as they were`() {
        // Arrange
        val storeId = "0a1B-2c.3_d~4"

        // Act
        val path = storeValuePath(storeId, "key")

        // Assert
        assertEquals("/stores/0a1B-2c.3_d~4/key", path)
    }

    @Test
    fun `non-ASCII and reserved characters are encoded as UTF-8 bytes`() {
        // Arrange
        val segment = "a b?é"

        // Act
        val encoded = encodePathSegment(segment)

        // Assert
        assertEquals("a%20b%3F%C3%A9", encoded)
    }
}
