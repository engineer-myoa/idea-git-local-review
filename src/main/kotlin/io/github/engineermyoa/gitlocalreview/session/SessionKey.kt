package io.github.engineermyoa.gitlocalreview.session

data class SessionKey(val repoRootPath: String, val spec: DiffSpec) {
    fun asString(): String = "$repoRootPath|${spec.storageKey()}"
}
