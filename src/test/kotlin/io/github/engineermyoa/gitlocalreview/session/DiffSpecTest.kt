package io.github.engineermyoa.gitlocalreview.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffSpecTest {
    @Test
    fun `storage keys are stable and distinct per spec`() {
        assertEquals("range:origin/develop", DiffSpec.CompareRefs("origin/develop", "HEAD").storageKey())
        assertEquals("staged", DiffSpec.Staged.storageKey())
        assertEquals("worktree", DiffSpec.WorkingTree.storageKey())
    }

    @Test
    fun `compare refs storage key keeps the range prefix when comparing against HEAD for backward compatibility`() {
        assertEquals("range:origin/main", DiffSpec.CompareRefs("origin/main", "HEAD").storageKey())
    }

    @Test
    fun `compare refs storage key uses both refs when comparing against a non-HEAD ref`() {
        assertEquals("refs:v1.0.0...origin/develop", DiffSpec.CompareRefs("v1.0.0", "origin/develop").storageKey())
    }

    @Test
    fun `session key combines repo root and spec`() {
        val key = SessionKey("/repo/a", DiffSpec.CompareRefs("origin/main", "HEAD"))
        assertEquals("/repo/a|range:origin/main", key.asString())
    }

    @Test
    fun `only staged and working tree specs are local changes mode`() {
        assertEquals(true, DiffSpec.Staged.isLocalChangesMode())
        assertEquals(true, DiffSpec.WorkingTree.isLocalChangesMode())
        assertEquals(false, DiffSpec.CompareRefs("origin/main", "HEAD").isLocalChangesMode())
    }
}
