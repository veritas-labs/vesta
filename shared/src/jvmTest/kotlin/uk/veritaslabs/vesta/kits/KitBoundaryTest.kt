package uk.veritaslabs.vesta.kits

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The kit dependency analysis of docs/plans/kits-extraction-implementation-plan.md as an executable
 * check. It reads every main-source `.kt` file, resolves its `import uk.veritaslabs.vesta.…` lines
 * to the file that declares the thing, and compares the kit of each side with
 * [KitMembership.allowedImports].
 *
 * Limits: references inside one package need no import and so are invisible here (the kits'
 * packages are separated by WP13, 17, 19, 21 and 23); wildcard imports and fully-qualified
 * references are ignored.
 *
 * [expectedViolations] lists today's known breaches. It can only shrink: a breach that is not in
 * the set fails the test, and so does a listed one that has been fixed (delete its line).
 */
class KitBoundaryTest {

    private data class SourceFile(val path: String, val pkg: String, val text: String) {
        val kit: Kit get() = KitMembership.kitOf(path)
    }

    private val vestaPackage = "uk.veritaslabs.vesta"
    private val topLevelDeclaration = Regex(
        "^(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|internal|private|expect|actual|data|sealed|enum|abstract|open|inline|value|annotation|suspend|const|lateinit|fun)\\s+)*" +
            "(?:class|interface|object|typealias|fun|val|var)\\s+(?:<[^>]*>\\s*)?(?:[\\w<>?,. ]*\\.)?(\\w+)",
        RegexOption.MULTILINE,
    )
    private val importLine = Regex("^import\\s+(uk\\.veritaslabs\\.vesta\\.[\\w.]+)(?:\\s+as\\s+\\w+)?\\s*$", RegexOption.MULTILINE)

    private fun mainFiles(): List<SourceFile> {
        val src = listOf(File("src"), File("shared/src")).first { it.isDirectory }
        val root = "uk/veritaslabs/vesta/"
        return src.listFiles { f -> f.isDirectory && f.name.endsWith("Main") }.orEmpty().flatMap { set ->
            set.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { file ->
                val text = file.readText()
                val path = file.invariantSeparatorsPath.substringAfter(root)
                val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1).orEmpty()
                SourceFile(path, pkg, text)
            }.toList()
        }.sortedBy { it.path }
    }

    /** "package.Name" to the files declaring a top-level Name in that package. */
    private fun declarations(files: List<SourceFile>): Map<String, List<SourceFile>> {
        val result = mutableMapOf<String, MutableList<SourceFile>>()
        files.forEach { file ->
            topLevelDeclaration.findAll(file.text).forEach { match ->
                result.getOrPut("${file.pkg}.${match.groupValues[1]}") { mutableListOf() }.add(file)
            }
        }
        return result
    }

    private fun violations(): Set<String> {
        val files = mainFiles()
        val declared = declarations(files)
        val found = sortedSetOf<String>()
        files.forEach { from ->
            importLine.findAll(from.text).forEach { match ->
                val imported = match.groupValues[1]
                // Nested imports (pkg.Outer.Inner) resolve through their longest declared prefix.
                val targets = generateSequence(imported) { it.substringBeforeLast('.', "").ifEmpty { null } }
                    .takeWhile { it.length > vestaPackage.length }
                    .firstNotNullOfOrNull { declared[it] }
                    .orEmpty()
                targets.filter { it !== from }.forEach { to ->
                    val allowed = from.kit == to.kit || to.kit in KitMembership.allowedImports.getValue(from.kit)
                    if (!allowed) found += "${from.kit.label}->${to.kit.label}: ${KitMembership.key(from.path)} -> ${KitMembership.key(to.path)}"
                }
            }
        }
        return found
    }

    @Test
    fun noImportCrossesAKitBoundaryThatIsNotAllowedExceptTheKnownOnes() {
        // Arrange
        val expected = expectedViolations

        // Act
        val actual = violations()

        // Assert
        assertEquals(
            emptySet(), actual - expected,
            "New kit boundary violations (fix the import, do not add to expectedViolations)",
        )
    }

    @Test
    fun everyKnownViolationIsStillPresentSoTheListOnlyShrinks() {
        // Arrange
        val expected = expectedViolations

        // Act
        val actual = violations()

        // Assert
        assertEquals(
            emptySet(), expected - actual,
            "These violations are fixed: delete them from expectedViolations",
        )
    }

    @Test
    fun theKitMembershipTableCoversTheFileCountsOfAppendixA() {
        // Arrange
        val files = mainFiles()

        // Act
        val counts = files.groupingBy { it.kit }.eachCount()

        // Assert
        KitMembership.expectedFileCounts.forEach { (kit, count) ->
            assertEquals(count, counts[kit] ?: 0, "files assigned to ${kit.label}")
        }
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun noKitMayImportVesta() {
        // Arrange
        val kits = Kit.entries.filter { it != Kit.Vesta }

        // Act
        val offenders = kits.filter { Kit.Vesta in KitMembership.allowedImports.getValue(it) }

        // Assert
        assertEquals(emptyList(), offenders)
    }

    private val expectedViolations: Set<String> = setOf(
        // Leaks listed in the plan's "What the dependency analysis found", section 3. Each WP deletes
        // the lines it fixes (WP1 domain types out of ui.*, WP2 platform inputs, WP3 constants,
        // WP4 seeding hook, WP5 document types, WP9 database, WP16 blobs seam).
        "blobs->vesta: blobs/BlobTransferService -> automerge/CookbookSources",
        "blobs->vesta: blobs/DeviceBlobRuntime -> automerge/CookbookSources",
    )
}
