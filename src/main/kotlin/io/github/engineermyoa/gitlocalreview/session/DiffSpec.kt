package io.github.engineermyoa.gitlocalreview.session

sealed interface DiffSpec {
    fun storageKey(): String

    data class CompareRefs(val fromRef: String, val toRef: String) : DiffSpec {
        override fun storageKey(): String =
            if (toRef == "HEAD") "range:$fromRef" else "refs:$fromRef...$toRef"
    }

    data object Staged : DiffSpec {
        override fun storageKey(): String = "staged"
    }

    data object WorkingTree : DiffSpec {
        override fun storageKey(): String = "worktree"
    }
}
