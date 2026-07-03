package io.github.engineermyoa.gitlocalreview.git

object GitOutputParsers {
    fun parseLsTree(output: String): Map<String, String> =
        output.split('\u0000')
            .filter { it.isNotEmpty() }
            .mapNotNull { entry -> parseTreeEntry(entry) }
            .toMap()

    fun parseLsFilesStaged(output: String): Map<String, String> =
        output.split('\u0000')
            .filter { it.isNotEmpty() }
            .mapNotNull { entry -> parseStagedEntry(entry) }
            .toMap()

    private fun parseTreeEntry(entry: String): Pair<String, String>? {
        val fields = entry.split('\t', limit = 2)
        if (fields.size < 2) return null
        val (meta, path) = fields
        val blobSha = meta.trim().split(' ').getOrNull(2) ?: return null
        return path to blobSha
    }

    private fun parseStagedEntry(entry: String): Pair<String, String>? {
        val fields = entry.split('\t', limit = 2)
        if (fields.size < 2) return null
        val (meta, path) = fields
        val parts = meta.trim().split(' ')
        val blobSha = parts.getOrNull(1) ?: return null
        val stage = parts.getOrNull(2) ?: return null
        return if (stage != "0") null else path to blobSha
    }
}
