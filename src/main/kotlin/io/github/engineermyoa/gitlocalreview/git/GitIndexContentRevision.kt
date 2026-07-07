package io.github.engineermyoa.gitlocalreview.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import java.nio.charset.Charset

class GitIndexContentRevision(
    private val project: Project,
    private val repository: GitRepository,
    private val path: FilePath,
    private val blobSha: String?
) : ByteBackedContentRevision {
    override fun getContentAsBytes(): ByteArray? {
        if (blobSha == null) return null
        val handler = GitBinaryHandler(project, repository.root, GitCommand.CAT_FILE)
        handler.setSilent(true)
        handler.addParameters("blob", blobSha)
        return try {
            handler.run()
        } catch (e: VcsException) {
            null
        }
    }

    override fun getContent(): String? {
        val bytes = getContentAsBytes() ?: return null
        return ContentRevisionCache.getAsString(bytes, path, Charset.defaultCharset())
    }

    override fun getFile(): FilePath = path

    override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber("staged:${blobSha?.take(8) ?: "-"}")
}
