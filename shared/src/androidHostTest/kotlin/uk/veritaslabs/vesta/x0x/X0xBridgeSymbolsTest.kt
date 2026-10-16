package uk.veritaslabs.vesta.x0x

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The embedded x0x daemon is bound by JNI symbol name: `ffi/x0x-ffi/src/lib.rs` exports
 * `Java_<package>_X0xBridge_<method>` and the JVM finds each one from the Kotlin class's
 * fully-qualified name and its `external` methods. Nothing in the Kotlin build notices a mismatch;
 * on a device it is an `UnsatisfiedLinkError`. No NDK or emulator is needed to catch it here, only
 * the compiled class (which is deliberately not initialised, its `init` block loads the library)
 * and the Rust source.
 *
 * `X0xBridge` is declared in the x0x kit but kept in this package on purpose; see its KDoc.
 */
class X0xBridgeSymbolsTest {

    private val bridgeClass = "uk.veritaslabs.vesta.x0x.X0xBridge"

    private fun libRs(): File =
        listOf(File("ffi/x0x-ffi/src/lib.rs"), File("../ffi/x0x-ffi/src/lib.rs")).first { it.isFile }

    private fun jniMangle(name: String): String = name.replace("_", "_1").replace('.', '_')

    private fun rustExports(): Set<String> =
        Regex("fn\\s+(Java_\\w+)\\s*\\(").findAll(libRs().readText()).map { it.groupValues[1] }.toSet()

    private fun kotlinExpectedSymbols(): Set<String> {
        val bridge = Class.forName(bridgeClass, false, javaClass.classLoader)
        val prefix = "Java_" + jniMangle(bridge.name) + "_"
        return bridge.declaredMethods.filter { Modifier.isNative(it.modifiers) }
            .map { prefix + jniMangle(it.name) }.toSet()
    }

    @Test
    fun everyNativeMethodOfTheKotlinBridgeHasARustExportOfTheSameName() {
        // Arrange
        val exports = rustExports()

        // Act
        val expected = kotlinExpectedSymbols()

        // Assert
        assertTrue(expected.isNotEmpty(), "the bridge declares no external methods")
        assertEquals(emptySet(), expected - exports, "Kotlin external methods with no matching Rust export")
    }

    @Test
    fun everyRustJniExportBelongsToTheKotlinBridge() {
        // Arrange
        val expected = kotlinExpectedSymbols()

        // Act
        val exports = rustExports()

        // Assert
        assertEquals(emptySet(), exports - expected, "Rust JNI exports with no matching Kotlin external method")
    }
}
