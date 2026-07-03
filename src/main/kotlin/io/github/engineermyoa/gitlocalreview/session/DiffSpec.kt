package io.github.engineermyoa.gitlocalreview.session

sealed interface DiffSpec {
    fun storageKey(): String

    data class BranchRange(val baseRef: String) : DiffSpec {
        override fun storageKey(): String = "range:$baseRef"
    }

    data object Staged : DiffSpec {
        override fun storageKey(): String = "staged"
    }

    data object WorkingTree : DiffSpec {
        override fun storageKey(): String = "worktree"
    }
}
