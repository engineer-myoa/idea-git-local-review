package io.github.engineermyoa.gitlocalreview.ui

import com.intellij.openapi.ListSelection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.github.engineermyoa.gitlocalreview.git.ReviewFile
import io.github.engineermyoa.gitlocalreview.session.DiffSpec
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.launch

class ReviewPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val controller = ReviewPanelController(project, ReviewPanelController.scope(project))

    private val repoCombo = ComboBox<GitRepository>()
    private val specCombo = ComboBox<SpecOption>()
    private val baseCombo = ComboBox<String>()
    private val refreshButton = JButton("Refresh")
    private val unreviewedOnlyCheckBox = JBCheckBox("Unreviewed only")
    private val progressLabel = JBLabel()
    private val tree = AsyncChangesTreeImpl.Changes(project, true, false)

    private var applyingInclusion = false
    private var populatingBaseCombo = false

    init {
        repoCombo.renderer = SimpleListCellRenderer.create("") { it.root.name }
        specCombo.renderer = SimpleListCellRenderer.create("") { it.label }
        baseCombo.isEditable = true

        toolbar = buildToolbar()
        setContent(JBScrollPane(tree))

        tree.setDoubleClickAndEnterKeyHandler { openSelectedDiff() }
        tree.setInclusionListener { onInclusionChanged() }
        unreviewedOnlyCheckBox.addActionListener { render(controller.model.value) }
        refreshButton.addActionListener { controller.refresh() }

        controller.openDiffRequest = ::openDiffAt

        initializeSelection()

        repoCombo.addActionListener { onRepositorySelected() }
        specCombo.addActionListener { onSpecChanged() }
        baseCombo.addActionListener { onBaseChanged() }

        subscribeToModel()
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
            add(specCombo)
            add(JBLabel("Base:"))
            add(baseCombo)
            add(refreshButton)
        }
        val progressRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(progressLabel)
            add(unreviewedOnlyCheckBox)
        }
        return JPanel(BorderLayout()).apply {
            add(comboRow, BorderLayout.NORTH)
            add(progressRow, BorderLayout.SOUTH)
        }
    }

    private fun initializeSelection() {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        repoCombo.model = DefaultComboBoxModel(repositories.toTypedArray())
        specCombo.model = DefaultComboBoxModel(SpecOption.entries.toTypedArray())
        val repository = repositories.firstOrNull() ?: return
        populateBaseCombo(repository)
        updateBaseComboEnabled()
        selectSessionFromCombos()
    }

    private fun onRepositorySelected() {
        val repository = selectedRepository() ?: return
        populateBaseCombo(repository)
        selectSessionFromCombos()
    }

    private fun onSpecChanged() {
        updateBaseComboEnabled()
        selectSessionFromCombos()
    }

    private fun onBaseChanged() {
        if (populatingBaseCombo) return
        if (currentSpecOption() == SpecOption.BRANCH_RANGE) selectSessionFromCombos()
    }

    private fun selectSessionFromCombos() {
        val repository = selectedRepository() ?: return
        controller.selectSession(repository, currentSpec())
    }

    private fun selectedRepository(): GitRepository? = repoCombo.selectedItem as? GitRepository

    private fun currentSpecOption(): SpecOption = specCombo.selectedItem as? SpecOption ?: SpecOption.BRANCH_RANGE

    private fun currentSpec(): DiffSpec =
        when (currentSpecOption()) {
            SpecOption.BRANCH_RANGE -> DiffSpec.BranchRange(baseCombo.selectedItem as? String ?: "")
            SpecOption.STAGED -> DiffSpec.Staged
            SpecOption.WORKING_TREE -> DiffSpec.WorkingTree
        }

    private fun updateBaseComboEnabled() {
        baseCombo.isEnabled = currentSpecOption() == SpecOption.BRANCH_RANGE
    }

    private fun populateBaseCombo(repository: GitRepository) {
        populatingBaseCombo = true
        try {
            val branchNames =
                (repository.branches.remoteBranches.map { it.name } + repository.branches.localBranches.map { it.name })
                    .distinct()
                    .sorted()
            baseCombo.model = DefaultComboBoxModel(branchNames.toTypedArray())
            baseCombo.selectedItem = detectDefaultBase(repository)
        } finally {
            populatingBaseCombo = false
        }
    }

    private fun detectDefaultBase(repository: GitRepository): String {
        val remoteNames = repository.branches.remoteBranches.map { it.name }
        return remoteNames.firstOrNull { it == "origin/main" }
            ?: remoteNames.firstOrNull { it == "origin/master" }
            ?: remoteNames.firstOrNull()
            ?: ""
    }

    private fun subscribeToModel() {
        ReviewPanelController.scope(project).launch {
            controller.model.collect { model ->
                ApplicationManager.getApplication().invokeLater { render(model) }
            }
        }
    }

    private fun render(model: ReviewPanelController.UiModel) {
        updateBaseComboEnabled()
        val displayedFiles = displayedFiles(model)
        tree.setChangesToDisplay(displayedFiles.map { it.change })
        applyIncludedChanges(model)
        updateProgressLabel(model)
        updateEmptyText(model, displayedFiles.size)
    }

    private fun displayedFiles(model: ReviewPanelController.UiModel): List<ReviewFile> =
        if (unreviewedOnlyCheckBox.isSelected) model.files.filterNot { it.relPath in model.reviewedPaths } else model.files

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
        val change = VcsTreeModelData.selected(tree).userObjects(Change::class.java).firstOrNull() ?: return
        val files = controller.model.value.files
        openDiffAt(files, files.firstOrNull { it.change == change }?.relPath)
    }

    private enum class SpecOption(val label: String) {
        BRANCH_RANGE("Branch Range"),
        STAGED("Staged"),
        WORKING_TREE("Working Tree")
    }
}
