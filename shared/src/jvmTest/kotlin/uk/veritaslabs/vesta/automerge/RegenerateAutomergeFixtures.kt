package uk.veritaslabs.vesta.automerge

import kotlin.test.Test

/**
 * NOT part of the normal test run: does nothing unless the environment variable `VESTA_REGENERATE_FIXTURES` is set.
 *
 *  - `VESTA_REGENERATE_FIXTURES=1` writes only the fixtures that are missing from `src/jvmTest/resources/fixtures/automerge`.
 *  - `VESTA_REGENERATE_FIXTURES=overwrite` replaces existing ones too. Do that only for a fixture that has never been
 *    released: a released fixture is a record of what real devices hold, and the tests that load it are the point.
 *
 * Run from the repository root:
 *
 *     VESTA_REGENERATE_FIXTURES=1 ./gradlew :shared:jvmTest --tests '*RegenerateAutomergeFixtures*'
 *
 * Automerge bytes are not reproducible (actor ids and change hashes are random), so a regenerated file differs from the
 * committed one even when its content is identical; the load tests assert content, never bytes.
 */
class RegenerateAutomergeFixtures {

    @Test
    fun regenerateFixtures() {
        // Arrange
        val mode = System.getenv("VESTA_REGENERATE_FIXTURES") ?: return
        val directory = AutomergeFixtures.sourceDirectory.also { it.mkdirs() }

        // Act
        AutomergeFixtures.generators.forEach { (name, generate) ->
            val file = directory.resolve(name)
            if (mode == "overwrite" || !file.exists()) file.writeBytes(generate())
        }

        // Assert
        AutomergeFixtures.generators.keys.forEach { check(directory.resolve(it).length() > 0) { "$it was not written" } }
    }
}
