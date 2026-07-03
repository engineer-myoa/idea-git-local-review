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
        val blobSha = meta.trim().split(' ')[2]
        return path to blobSha
    }

    private fun parseStagedEntry(entry: String): Pair<String, String>? {
        val fields = entry.split('\t', limit = 2)
        if (fields.size < 2) return null
        val (meta, path) = fields
        val parts = meta.trim().split(' ')
        return if (parts[2] != "0") null else path to parts[1]
    }
}
