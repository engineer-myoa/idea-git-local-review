package io.github.engineermyoa.gitlocalreview.actions

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.github.engineermyoa.gitlocalreview.ui.ReviewPanelController

class MarkReviewedAndOpenNextAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = findContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (controller, relPath) = findContext(e) ?: return
        controller.markReviewedAndOpenNext(relPath)
    }

    private fun findContext(e: AnActionEvent): Pair<ReviewPanelController, String>? {
        val request = e.getData(DiffDataKeys.DIFF_REQUEST) ?: return null
        val controller = request.getUserData(ReviewPanelController.CONTROLLER_KEY) ?: return null
        val relPath = request.getUserData(ReviewPanelController.REL_PATH_KEY) ?: return null
        return controller to relPath
    }
}
