package io.github.engineermyoa.gitlocalreview.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import io.github.engineermyoa.gitlocalreview.session.DiffSpec

data class ReviewFile(val change: Change, val relPath: String, val fingerprint: String)

sealed interface DiffResult {
    data class Success(val files: List<ReviewFile>) : DiffResult
    data class Failure(val message: String) : DiffResult
}

class DiffProvider(private val project: Project) {

    fun collect(repository: GitRepository, spec: DiffSpec): DiffResult = try {
        when (spec) {
            is DiffSpec.BranchRange -> collectBranchRange(repository, spec.baseRef)
            DiffSpec.Staged -> collectStaged(repository)
            DiffSpec.WorkingTree -> collectWorkingTree(repository)
        }
    } catch (e: VcsException) {
        DiffResult.Failure(e.message ?: "git operation failed")
    }

    private fun collectBranchRange(repository: GitRepository, baseRef: String): DiffResult {
        val changes = GitChangeUtils.getThreeDotDiffOrThrow(repository, baseRef, GitUtil.HEAD)
        val blobs = runGit(repository, GitCommand.LS_TREE, listOf("-r", "-z", GitUtil.HEAD))
            .let(GitOutputParsers::parseLsTree)
        val files = changes.map { change ->
            val relPath = relativePath(repository, change)
            ReviewFile(change, relPath, blobs[relPath] ?: Fingerprints.DELETED)
        }
        return DiffResult.Success(files.sortedBy { it.relPath })
    }

    private fun collectStaged(repository: GitRepository): DiffResult {
        val staged = GitChangeUtils.getStagedChanges(project, repository.root)
        val blobs = runGit(repository, GitCommand.LS_FILES, listOf("-s", "-z"))
            .let(GitOutputParsers::parseLsFilesStaged)
        val files = staged.map { diffChange ->
            val relPath = VcsFileUtil.relativePath(repository.root, diffChange.filePath)
            val blobSha = blobs[relPath]
            ReviewFile(toChange(repository, diffChange, blobSha), relPath, blobSha ?: Fingerprints.DELETED)
        }
        return DiffResult.Success(files.sortedBy { it.relPath })
    }

    private fun collectWorkingTree(repository: GitRepository): DiffResult {
        val root = repository.root
        val changes = ChangeListManager.getInstance(project).allChanges.filter { change ->
            val filePath = change.afterRevision?.file ?: change.beforeRevision?.file
            filePath != null && VcsFileUtil.isAncestor(root, filePath)
        }
        val files = changes.map { change ->
            val relPath = relativePath(repository, change)
            val content = change.afterRevision?.content
            ReviewFile(change, relPath, content?.let(Fingerprints::sha256) ?: Fingerprints.DELETED)
        }
        return DiffResult.Success(files.sortedBy { it.relPath })
    }

    private fun toChange(repository: GitRepository, diffChange: GitChangeUtils.GitDiffChange, indexBlobSha: String?): Change {
        val before = diffChange.beforePath?.let { path ->
            GitContentRevision.createRevision(path, GitRevisionNumber.HEAD, project)
        }
        val after = diffChange.afterPath?.let { path ->
            GitIndexContentRevision(project, repository, path, indexBlobSha)
        }
        return Change(before, after)
    }

    private fun relativePath(repository: GitRepository, change: Change): String {
        val filePath = change.afterRevision?.file ?: change.beforeRevision!!.file
        return VcsFileUtil.relativePath(repository.root, filePath)
    }

    private fun runGit(repository: GitRepository, command: GitCommand, params: List<String>): String {
        val handler = GitLineHandler(project, repository.root, command)
        handler.addParameters(params)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) throw VcsException(result.errorOutputAsJoinedString)
        return result.outputAsJoinedString
    }
}
