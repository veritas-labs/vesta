package uk.veritaslabs.vesta.licenses

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** JVM-only: reading a compose resource needs the desktop classpath; the Android host test has no instrumentation to do it. */
class BundledOpenSourceLicensesTest {

    @Test
    fun theBundledListLoadsAndIncludesTheCoreLibraries() = runTest {
        // Arrange
        // (the list is the committed composeResources/files/licenses.json)

        // Act
        val names = loadOpenSourceLicenses().map { it.name }

        // Assert
        assertTrue("org.automerge:automerge" in names, "Automerge should be listed")
        assertTrue("crates.io:x0x" in names, "x0x should be listed")
    }
}
