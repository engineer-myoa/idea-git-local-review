package io.github.engineermyoa.gitlocalreview.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitOutputParsersTest {
    @Test
    fun `parses ls-tree -r -z output into path to blob map`() {
        val out = "100644 blob a1b2c3\tsrc/Main.kt\u0000100644 blob d4e5f6\tREADME.md\u0000"
        assertEquals(
            mapOf("src/Main.kt" to "a1b2c3", "README.md" to "d4e5f6"),
            GitOutputParsers.parseLsTree(out)
        )
    }

    @Test
    fun `parses ls-files -s -z output and keeps stage0 entries only`() {
        val out = "100644 a1b2c3 0\tsrc/Main.kt\u0000100644 d4e5f6 2\tconflict.kt\u0000"
        assertEquals(mapOf("src/Main.kt" to "a1b2c3"), GitOutputParsers.parseLsFilesStaged(out))
    }

    @Test
    fun `handles empty output and paths with spaces`() {
        assertEquals(emptyMap<String, String>(), GitOutputParsers.parseLsTree(""))
        val out = "100644 blob abc123\tsrc/My File.kt\u0000"
        assertEquals(mapOf("src/My File.kt" to "abc123"), GitOutputParsers.parseLsTree(out))
    }

    @Test
    fun `sha256 is deterministic`() {
        assertEquals(Fingerprints.sha256("x"), Fingerprints.sha256("x"))
    }
}
