package io.github.engineermyoa.gitlocalreview.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

class GitIndexContentRevision(
    private val project: Project,
    private val repository: GitRepository,
    private val path: FilePath,
    private val blobSha: String?
) : ContentRevision {
    override fun getContent(): String? {
        if (blobSha == null) return null
        val handler = GitLineHandler(project, repository.root, GitCommand.CAT_FILE)
        handler.addParameters("blob", blobSha)
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.outputAsJoinedString else null
    }

    override fun getFile(): FilePath = path

    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
}
