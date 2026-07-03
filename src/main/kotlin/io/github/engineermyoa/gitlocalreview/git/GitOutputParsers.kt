package io.github.engineermyoa.gitlocalreview.git

object GitOutputParsers {
    fun parseLsTree(output: String): Map<String, String> =
        output.split('\u0000')
            .filter { it.isNotEmpty() }
            .associate { entry ->
                val (meta, path) = entry.split('\t', limit = 2)
                val blobSha = meta.trim().split(' ')[2]
                path to blobSha
            }

    fun parseLsFilesStaged(output: String): Map<String, String> =
        output.split('\u0000')
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val (meta, path) = entry.split('\t', limit = 2)
                val parts = meta.trim().split(' ')
                if (parts[2] != "0") null else path to parts[1]
            }.toMap()
}
