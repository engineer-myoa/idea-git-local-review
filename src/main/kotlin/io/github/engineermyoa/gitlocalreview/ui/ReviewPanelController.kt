package io.github.engineermyoa.gitlocalreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import io.github.engineermyoa.gitlocalreview.git.DiffProvider
import io.github.engineermyoa.gitlocalreview.git.DiffResult
import io.github.engineermyoa.gitlocalreview.git.ReviewFile
import io.github.engineermyoa.gitlocalreview.session.DiffSpec
import io.github.engineermyoa.gitlocalreview.session.SessionKey
import io.github.engineermyoa.gitlocalreview.state.ReviewStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class GitLocalReviewScope(val project: Project, val cs: CoroutineScope)

class ReviewPanelController(private val project: Project, private val cs: CoroutineScope) {

    data class UiModel(
        val files: List<ReviewFile>,
        val reviewedPaths: Set<String>,
        val failure: String?,
        val repository: GitRepository?,
        val spec: DiffSpec
    )

    var openDiffRequest: ((List<ReviewFile>, String) -> Unit)? = null

    private val diffProvider = DiffProvider(project)
    private val stateService = ReviewStateService.getInstance(project)

    private val _model = MutableStateFlow(
        UiModel(files = emptyList(), reviewedPaths = emptySet(), failure = null, repository = null, spec = DiffSpec.WorkingTree)
    )
    val model: StateFlow<UiModel> = _model

    private var refreshJob: Job? = null
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    init {
        subscribeToRefreshTrigger()
        subscribeToAutoRefresh()
    }

    @OptIn(FlowPreview::class)
    private fun subscribeToRefreshTrigger() {
        cs.launch {
            refreshTrigger
                .debounce(DEBOUNCE_MILLIS.toLong())
                .collect { refresh() }
        }
    }

    fun selectSession(repository: GitRepository, spec: DiffSpec) {
        _model.update {
            it.copy(files = emptyList(), reviewedPaths = emptySet(), failure = null, repository = repository, spec = spec)
        }
        refresh()
    }

    fun refresh() {
        val repository = model.value.repository ?: return
        val spec = model.value.spec
        refreshJob?.cancel()
        refreshJob = cs.launch(Dispatchers.Default) {
            val result = diffProvider.collect(repository, spec)
            if (!isActive) return@launch
            applyResult(repository, spec, result)
        }
    }

    fun setViewed(files: List<ReviewFile>, viewed: Boolean) {
        val repository = model.value.repository ?: return
        val sessionKey = SessionKey(repository.root.path, model.value.spec)
        stateService.markViewed(sessionKey, files.map { it.relPath to it.fingerprint }, viewed)
        val relPaths = files.map { it.relPath }.toSet()
        _model.update { current ->
            current.copy(
                reviewedPaths = if (viewed) current.reviewedPaths + relPaths else current.reviewedPaths - relPaths
            )
        }
    }

    fun nextUnreviewed(afterRelPath: String?): ReviewFile? {
        val current = model.value
        val startIndex = afterRelPath?.let { path ->
            val index = current.files.indexOfFirst { it.relPath == path }
            if (index < 0) 0 else index + 1
        } ?: 0
        return current.files.drop(startIndex).firstOrNull { it.relPath !in current.reviewedPaths }
    }

    fun markReviewedAndOpenNext(relPath: String) {
        val file = model.value.files.firstOrNull { it.relPath == relPath } ?: return
        setViewed(listOf(file), true)
        val next = nextUnreviewed(relPath) ?: return
        val files = model.value.files
        ApplicationManager.getApplication().invokeLater {
            openDiffRequest?.invoke(files, next.relPath)
        }
    }

    private fun applyResult(repository: GitRepository, spec: DiffSpec, result: DiffResult) {
        when (result) {
            is DiffResult.Success -> applySuccess(repository, spec, result.files)
            is DiffResult.Failure -> _model.update { it.copy(repository = repository, spec = spec, failure = result.message) }
        }
    }

    private fun applySuccess(repository: GitRepository, spec: DiffSpec, files: List<ReviewFile>) {
        val sessionKey = SessionKey(repository.root.path, spec)
        val (matchedPaths, invalidatedPaths) = reconcileViewedState(sessionKey, files)
        val newFilesPaths = files.map { it.relPath }.toSet()
        _model.update { current ->
            current.copy(
                files = files,
                reviewedPaths = ((current.reviewedPaths - invalidatedPaths) + matchedPaths) intersect newFilesPaths,
                failure = null,
                repository = repository,
                spec = spec
            )
        }
    }

    private fun reconcileViewedState(sessionKey: SessionKey, files: List<ReviewFile>): Pair<Set<String>, Set<String>> {
        val storedFingerprints = stateService.viewedFingerprints(sessionKey)
        val matchedPaths = mutableSetOf<String>()
        val invalidatedPaths = mutableSetOf<String>()
        val invalidated = mutableListOf<Pair<String, String>>()
        for (file in files) {
            val storedFingerprint = storedFingerprints[file.relPath] ?: continue
            if (storedFingerprint == file.fingerprint) {
                matchedPaths += file.relPath
            } else {
                invalidatedPaths += file.relPath
                invalidated += file.relPath to storedFingerprint
            }
        }
        if (invalidated.isNotEmpty()) {
            stateService.markViewed(sessionKey, invalidated, viewed = false)
        }
        return matchedPaths to invalidatedPaths
    }

    private fun subscribeToAutoRefresh() {
        val connection = project.messageBus.connect(cs)
        connection.subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repository ->
                if (repository.root == model.value.repository?.root) refreshTrigger.tryEmit(Unit)
            }
        )
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (model.value.repository != null && isFileSystemBackedSpec(model.value.spec)) {
                        refreshTrigger.tryEmit(Unit)
                    }
                }
            }
        )
    }

    private fun isFileSystemBackedSpec(spec: DiffSpec): Boolean = spec == DiffSpec.Staged || spec == DiffSpec.WorkingTree

    companion object {
        private const val DEBOUNCE_MILLIS = 500
        val CONTROLLER_KEY: Key<ReviewPanelController> = Key.create("GitLocalReview.controller")
        val REL_PATH_KEY: Key<String> = Key.create("GitLocalReview.relPath")
        fun scope(project: Project): CoroutineScope = project.service<GitLocalReviewScope>().cs
    }
}
