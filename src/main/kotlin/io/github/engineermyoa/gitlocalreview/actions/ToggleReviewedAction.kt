package io.github.engineermyoa.gitlocalreview.actions

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import io.github.engineermyoa.gitlocalreview.ui.ReviewPanelController

class ToggleReviewedAction : DumbAwareAction(MARK_TEXT, null, AllIcons.Actions.Checked) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val context = findContext(e)
        val reviewed = context != null && isReviewed(context)
        e.presentation.isEnabledAndVisible = context != null
        e.presentation.text = if (reviewed) UNMARK_TEXT else MARK_TEXT
        Toggleable.setSelected(e.presentation, reviewed)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (controller, relPath) = findContext(e) ?: return
        controller.toggleReviewed(relPath)
    }

    private fun isReviewed(context: Pair<ReviewPanelController, String>): Boolean {
        val (controller, relPath) = context
        return relPath in controller.model.value.reviewedPaths
    }

    private fun findContext(e: AnActionEvent): Pair<ReviewPanelController, String>? =
        findDiffRequestContext(e) ?: findPanelContext(e)

    private fun findDiffRequestContext(e: AnActionEvent): Pair<ReviewPanelController, String>? {
        val request = e.getData(DiffDataKeys.DIFF_REQUEST) ?: return null
        val controller = request.getUserData(ReviewPanelController.CONTROLLER_KEY) ?: return null
        val relPath = request.getUserData(ReviewPanelController.REL_PATH_KEY) ?: return null
        return controller to relPath
    }

    private fun findPanelContext(e: AnActionEvent): Pair<ReviewPanelController, String>? {
        val controller = e.getData(ReviewPanelController.DATA_KEY) ?: return null
        val relPath = e.getData(ReviewPanelController.SELECTED_REL_PATH) ?: return null
        return controller to relPath
    }

    companion object {
        const val ACTION_ID = "GitLocalReview.ToggleReviewed"
        private const val MARK_TEXT = "Mark as Reviewed"
        private const val UNMARK_TEXT = "Unmark as Reviewed"
    }
}
