package io.github.engineermyoa.gitlocalreview.ui

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import git4idea.GitUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.github.engineermyoa.gitlocalreview.actions.MarkReviewedAndOpenNextAction
import io.github.engineermyoa.gitlocalreview.git.ReviewFile
import io.github.engineermyoa.gitlocalreview.session.DiffSpec
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReviewPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val controller = ReviewPanelController(project, ReviewPanelController.scope(project))

    private val repoCombo = ComboBox<GitRepository>()
    private val modeCombo = ComboBox<Mode>()
    private val scopeCombo = ComboBox<Scope>()
    private val fromRefLabel = JBLabel("A:")
    private val fromRefCombo = ComboBox<String>()
    private val toRefLabel = JBLabel("B:")
    private val toRefCombo = ComboBox<String>()
    private val refreshButton = JButton("Refresh")
    private val unreviewedOnlyCheckBox = JBCheckBox("Unreviewed only")
    private val progressLabel = JBLabel()
    private val tree = AsyncChangesTreeImpl.Changes(project, true, false)

    private var applyingInclusion = false
    private var populatingRefCombos = false
    private var updatingRepoCombo = false
    private var lastDisplayedSignature: List<Pair<String, String>> = emptyList()

    init {
        repoCombo.renderer = SimpleListCellRenderer.create("") { it.root.name }
        modeCombo.renderer = SimpleListCellRenderer.create("") { it.label }
        scopeCombo.renderer = SimpleListCellRenderer.create("") { it.label }
        fromRefCombo.isEditable = true
        toRefCombo.isEditable = true

        toolbar = buildToolbar()
        setContent(JBScrollPane(tree))

        tree.setTreeStateStrategy(ChangesTree.KEEP_NON_EMPTY)
        tree.setDoubleClickAndEnterKeyHandler { openSelectedDiff() }
        tree.setInclusionListener { onInclusionChanged() }
        tree.installPopupHandler(buildTreePopupGroup())
        unreviewedOnlyCheckBox.addActionListener { render(controller.model.value) }
        refreshButton.addActionListener { onRefreshRequested() }

        controller.openDiffRequest = ::openDiffAt

        initializeSelection()

        repoCombo.addActionListener { onRepositorySelected() }
        modeCombo.addActionListener { onModeChanged() }
        scopeCombo.addActionListener { onScopeChanged() }
        fromRefCombo.addActionListener { onFromRefChanged() }
        toRefCombo.addActionListener { onToRefChanged() }

        subscribeToModel()
        subscribeToRepositoryMappingChanges()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink.set(ReviewPanelController.DATA_KEY, controller)
        sink.set(ReviewPanelController.SELECTED_REL_PATH, selectedRelPath())
        sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, selectedNavigatables())
        val virtualFiles = selectedAfterVirtualFiles()
        sink.set(CommonDataKeys.VIRTUAL_FILE, virtualFiles.firstOrNull())
        sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, virtualFiles.takeIf { it.isNotEmpty() }?.toTypedArray())
        sink.set(VcsDataKeys.VIRTUAL_FILES, virtualFiles.takeIf { it.isNotEmpty() }?.asIterable())
        sink.set(VcsDataKeys.CHANGES, selectedChanges().takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    fun openDiffAt(files: List<ReviewFile>, startRelPath: String?) {
        if (files.isEmpty()) return
        val index = files.indexOfFirst { it.relPath == startRelPath }.coerceAtLeast(0)
        val context = ShowDiffContext()
        files.forEach { rf ->
            context.putChangeContext(rf.change, ReviewPanelController.REL_PATH_KEY, rf.relPath)
            context.putChangeContext(rf.change, ReviewPanelController.CONTROLLER_KEY, controller)
        }
        ShowDiffAction.showDiffForChange(project, ListSelection.createAt(files.map { it.change }, index), context)
    }

    private fun buildToolbar(): JComponent {
        val comboRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(repoCombo)
            add(modeCombo)
            add(scopeCombo)
            add(fromRefLabel)
            add(fromRefCombo)
            add(toRefLabel)
            add(toRefCombo)
            add(refreshButton)
        }
        val progressRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(progressLabel)
            add(unreviewedOnlyCheckBox)
            add(buildActionToolbar())
        }
        return JPanel(BorderLayout()).apply {
            add(comboRow, BorderLayout.NORTH)
            add(progressRow, BorderLayout.SOUTH)
        }
    }

    private fun buildActionToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            ActionManager.getInstance().getAction(MarkReviewedAndOpenNextAction.ACTION_ID)?.let(::add)
        }
        val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        actionToolbar.targetComponent = this
        return actionToolbar.component
    }

    private fun buildTreePopupGroup(): DefaultActionGroup = DefaultActionGroup().apply {
        add(object : DumbAwareAction("Show Diff") {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedChange() != null
            }

            override fun actionPerformed(e: AnActionEvent) = openSelectedDiff()
        })
        addSeparator()
        ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE)?.let(::add)
        ActionManager.getInstance().getAction(MarkReviewedAndOpenNextAction.ACTION_ID)?.let(::add)
        addSeparator()
        add(buildGitSubmenu())
    }

    private fun buildGitSubmenu(): ActionGroup = GitSubmenuActionGroup()

    private inner class GitSubmenuActionGroup : ActionGroup(GIT_SUBMENU_TEXT, true) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            val actionManager = ActionManager.getInstance()
            val vcsActions = listOfNotNull(
                actionManager.getAction(ACTION_SHOW_HISTORY),
                actionManager.getAction(ACTION_ANNOTATE),
                actionManager.getAction(ACTION_COMPARE_WITH_BRANCH)
            )
            if (!controller.model.value.spec.isLocalChangesMode()) return vcsActions.toTypedArray()
            val rollback = actionManager.getAction(IdeActions.CHANGES_VIEW_ROLLBACK) ?: return vcsActions.toTypedArray()
            return (vcsActions + Separator.create() + rollback).toTypedArray()
        }
    }

    private fun initializeSelection() {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        repoCombo.model = DefaultComboBoxModel(repositories.toTypedArray())
        modeCombo.model = DefaultComboBoxModel(Mode.entries.toTypedArray())
        modeCombo.selectedItem = Mode.COMPARE_REFS
        scopeCombo.model = DefaultComboBoxModel(Scope.entries.toTypedArray())
        val repository = repositories.firstOrNull() ?: return
        initializeRepositorySelection(repository)
    }

    private fun initializeRepositorySelection(repository: GitRepository) {
        populateRefCombos(repository)
        updateModeControlsVisibility()
        selectSessionFromCombos()
    }

    private fun onRepositorySelected() {
        if (updatingRepoCombo) return
        val repository = selectedRepository() ?: return
        populateRefCombos(repository)
        selectSessionFromCombos()
    }

    private fun subscribeToRepositoryMappingChanges() {
        project.messageBus.connect(ReviewPanelController.scope(project)).subscribe(
            VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
            VcsRepositoryMappingListener {
                ApplicationManager.getApplication().invokeLater { onRepositoryMappingChanged() }
            }
        )
    }

    private fun onRepositoryMappingChanged() {
        val previousSelection = selectedRepository()
        val repositories = GitRepositoryManager.getInstance(project).repositories
        val restoredSelection = previousSelection?.takeIf { it in repositories }
        updatingRepoCombo = true
        try {
            repoCombo.model = DefaultComboBoxModel(repositories.toTypedArray())
            if (restoredSelection != null) repoCombo.selectedItem = restoredSelection
        } finally {
            updatingRepoCombo = false
        }
        if (restoredSelection == null) selectedRepository()?.let(::initializeRepositorySelection)
    }

    private fun onModeChanged() {
        updateModeControlsVisibility()
        selectSessionFromCombos()
    }

    private fun onScopeChanged() {
        if (currentMode() == Mode.LOCAL_CHANGES) selectSessionFromCombos()
    }

    private fun onFromRefChanged() {
        if (populatingRefCombos) return
        if (currentMode() == Mode.COMPARE_REFS) selectSessionFromCombos()
    }

    private fun onToRefChanged() {
        if (populatingRefCombos) return
        if (currentMode() == Mode.COMPARE_REFS) selectSessionFromCombos()
    }

    private fun onRefreshRequested() {
        if (repoCombo.itemCount == 0) onRepositoryMappingChanged() else controller.refresh()
    }

    private fun selectSessionFromCombos() {
        val repository = selectedRepository() ?: return
        controller.selectSession(repository, currentSpec())
    }

    private fun selectedRepository(): GitRepository? = repoCombo.selectedItem as? GitRepository

    private fun currentMode(): Mode = modeCombo.selectedItem as? Mode ?: Mode.LOCAL_CHANGES

    private fun currentScope(): Scope = scopeCombo.selectedItem as? Scope ?: Scope.WORKING_TREE

    private fun currentSpec(): DiffSpec =
        when (currentMode()) {
            Mode.LOCAL_CHANGES -> when (currentScope()) {
                Scope.STAGED -> DiffSpec.Staged
                Scope.WORKING_TREE -> DiffSpec.WorkingTree
            }
            Mode.COMPARE_REFS -> DiffSpec.CompareRefs(
                fromRefCombo.selectedItem as? String ?: "",
                toRefCombo.selectedItem as? String ?: GitUtil.HEAD
            )
        }

    private fun updateModeControlsVisibility() {
        val localChanges = currentMode() == Mode.LOCAL_CHANGES
        scopeCombo.isVisible = localChanges
        fromRefLabel.isVisible = !localChanges
        fromRefCombo.isVisible = !localChanges
        toRefLabel.isVisible = !localChanges
        toRefCombo.isVisible = !localChanges
    }

    private fun populateRefCombos(repository: GitRepository) {
        val remoteNames = repository.branches.remoteBranches.map { it.name }
        val branchNames = (remoteNames + repository.branches.localBranches.map { it.name }).distinct().sorted()
        val heuristicDefault = heuristicDefaultBase(remoteNames)
        setRefComboModels(branchNames, heuristicDefault)
        detectRemoteHeadDefaultBase(repository, remoteNames, heuristicDefault)
        loadTagsAsync(repository, branchNames, heuristicDefault)
    }

    private fun setRefComboModels(refNames: List<String>, fromRefDefault: String) {
        populatingRefCombos = true
        try {
            val previousFrom = fromRefCombo.selectedItem as? String
            val previousTo = toRefCombo.selectedItem as? String
            fromRefCombo.model = DefaultComboBoxModel(refNames.toTypedArray())
            fromRefCombo.selectedItem = previousFrom ?: fromRefDefault
            toRefCombo.model = DefaultComboBoxModel((listOf(GitUtil.HEAD) + refNames).toTypedArray())
            toRefCombo.selectedItem = previousTo ?: GitUtil.HEAD
        } finally {
            populatingRefCombos = false
        }
    }

    private fun loadTagsAsync(repository: GitRepository, branchNames: List<String>, heuristicDefault: String) {
        val expectedFrom = fromRefCombo.selectedItem as? String
        val expectedTo = toRefCombo.selectedItem as? String
        ReviewPanelController.scope(project).launch(Dispatchers.Default) {
            val tags = controller.listTags(repository)
            if (tags.isEmpty()) return@launch
            val refNames = (branchNames + tags).distinct().sorted()
            ApplicationManager.getApplication().invokeLater {
                mergeTagsIntoRefCombos(repository, refNames, heuristicDefault, expectedFrom, expectedTo)
            }
        }
    }

    private fun mergeTagsIntoRefCombos(
        repository: GitRepository,
        refNames: List<String>,
        heuristicDefault: String,
        expectedFrom: String?,
        expectedTo: String?
    ) {
        if (selectedRepository() != repository) return
        if (fromRefCombo.selectedItem != expectedFrom || toRefCombo.selectedItem != expectedTo) return
        setRefComboModels(refNames, heuristicDefault)
    }

    private fun heuristicDefaultBase(remoteNames: List<String>): String =
        remoteNames.firstOrNull { it == "origin/main" }
            ?: remoteNames.firstOrNull { it == "origin/master" }
            ?: remoteNames.firstOrNull()
            ?: ""

    private fun detectRemoteHeadDefaultBase(repository: GitRepository, remoteNames: List<String>, heuristicDefault: String) {
        ReviewPanelController.scope(project).launch(Dispatchers.Default) {
            val detected = controller.detectRemoteHeadBase(repository)?.takeIf { it in remoteNames } ?: return@launch
            if (detected == heuristicDefault) return@launch
            ApplicationManager.getApplication().invokeLater {
                adoptDetectedBase(repository, heuristicDefault, detected)
            }
        }
    }

    private fun adoptDetectedBase(repository: GitRepository, expectedCurrent: String, detected: String) {
        if (selectedRepository() != repository) return
        if (fromRefCombo.selectedItem != expectedCurrent) return
        populatingRefCombos = true
        try {
            fromRefCombo.selectedItem = detected
        } finally {
            populatingRefCombos = false
        }
        if (currentMode() == Mode.COMPARE_REFS) selectSessionFromCombos()
    }

    private fun subscribeToModel() {
        ReviewPanelController.scope(project).launch {
            controller.model.collect { model ->
                ApplicationManager.getApplication().invokeLater { render(model) }
            }
        }
    }

    private fun render(model: ReviewPanelController.UiModel) {
        updateModeControlsVisibility()
        val displayedFiles = displayedFiles(model)
        updateTreeContentIfChanged(displayedFiles)
        applyIncludedChanges(model)
        updateProgressLabel(model)
        updateEmptyText(model, displayedFiles.size)
    }

    private fun displayedFiles(model: ReviewPanelController.UiModel): List<ReviewFile> =
        if (unreviewedOnlyCheckBox.isSelected) model.files.filterNot { it.relPath in model.reviewedPaths } else model.files

    private fun updateTreeContentIfChanged(displayedFiles: List<ReviewFile>) {
        val signature = displayedFiles.map { it.relPath to it.fingerprint }
        if (signature == lastDisplayedSignature) return
        lastDisplayedSignature = signature
        tree.setChangesToDisplay(displayedFiles.map { it.change })
    }

    private fun applyIncludedChanges(model: ReviewPanelController.UiModel) {
        applyingInclusion = true
        try {
            val includedChanges = model.files.filter { it.relPath in model.reviewedPaths }.map { it.change }
            tree.setIncludedChanges(includedChanges)
        } finally {
            applyingInclusion = false
        }
    }

    private fun updateProgressLabel(model: ReviewPanelController.UiModel) {
        val total = model.files.size
        val reviewed = model.reviewedPaths.size
        val percent = if (total == 0) 0 else reviewed * 100 / total
        progressLabel.text = "Reviewed $reviewed / $total ($percent%)"
    }

    private fun updateEmptyText(model: ReviewPanelController.UiModel, displayedCount: Int) {
        tree.setEmptyText(
            when {
                model.repository == null -> "No Git repository found in this project."
                model.failure != null -> model.failure
                model.files.isEmpty() || displayedCount == 0 -> "Nothing to review"
                else -> ""
            }
        )
    }

    private fun onInclusionChanged() {
        if (applyingInclusion) return
        val model = controller.model.value
        val includedChanges = tree.includedChanges.toSet()
        val includedPaths = model.files.filter { it.change in includedChanges }.map { it.relPath }.toSet()
        val newlyReviewed = includedPaths - model.reviewedPaths
        val newlyUnreviewed = model.reviewedPaths - includedPaths
        if (newlyReviewed.isNotEmpty()) controller.setViewed(model.files.filter { it.relPath in newlyReviewed }, viewed = true)
        if (newlyUnreviewed.isNotEmpty()) controller.setViewed(model.files.filter { it.relPath in newlyUnreviewed }, viewed = false)
    }

    private fun openSelectedDiff() {
        val change = selectedChange() ?: return
        val files = controller.model.value.files
        openDiffAt(files, files.firstOrNull { it.change == change }?.relPath)
    }

    private fun selectedChanges(): List<Change> = VcsTreeModelData.selected(tree).userObjects(Change::class.java)

    private fun selectedChange(): Change? = selectedChanges().firstOrNull()

    private fun selectedRelPath(): String? {
        val change = selectedChange() ?: return null
        return controller.model.value.files.firstOrNull { it.change == change }?.relPath
    }

    private fun selectedAfterVirtualFiles(): List<VirtualFile> =
        selectedChanges().mapNotNull { it.afterRevision?.file?.virtualFile }

    private fun selectedNavigatables(): Array<Navigatable> =
        selectedAfterVirtualFiles().map { OpenFileDescriptor(project, it) }.toTypedArray()

    private enum class Mode(val label: String) {
        LOCAL_CHANGES("Local Changes"),
        COMPARE_REFS("Compare Refs")
    }

    private enum class Scope(val label: String) {
        STAGED("Staged"),
        WORKING_TREE("Working Tree")
    }

    private companion object {
        const val GIT_SUBMENU_TEXT = "Git"
        const val ACTION_SHOW_HISTORY = "Vcs.ShowTabbedFileHistory"
        const val ACTION_ANNOTATE = "Annotate"
        const val ACTION_COMPARE_WITH_BRANCH = "Git.CompareWithBranch"
    }
}
