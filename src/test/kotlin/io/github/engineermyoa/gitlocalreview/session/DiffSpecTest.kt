package io.github.engineermyoa.gitlocalreview.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffSpecTest {
    @Test
    fun `storage keys are stable and distinct per spec`() {
        assertEquals("range:origin/develop", DiffSpec.BranchRange("origin/develop").storageKey())
        assertEquals("staged", DiffSpec.Staged.storageKey())
        assertEquals("worktree", DiffSpec.WorkingTree.storageKey())
    }

    @Test
    fun `session key combines repo root and spec`() {
        val key = SessionKey("/repo/a", DiffSpec.BranchRange("origin/main"))
        assertEquals("/repo/a|range:origin/main", key.asString())
    }
}
