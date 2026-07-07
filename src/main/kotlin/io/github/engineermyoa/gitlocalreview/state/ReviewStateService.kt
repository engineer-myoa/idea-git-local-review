package io.github.engineermyoa.gitlocalreview.state

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.engineermyoa.gitlocalreview.session.SessionKey
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable

@Service(Service.Level.PROJECT)
@State(name = "GitLocalReviewViewedState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReviewStateService : SerializablePersistentStateComponent<ReviewStateService.State>(State()) {

    @Serializable
    data class State(val sessions: List<SessionViewedState> = emptyList())

    @Serializable
    data class SessionViewedState(
        val sessionKey: String,
        val lastUpdated: Long = 0L,
        val viewedFiles: Map<String, String> = emptyMap()
    )

    fun viewedFingerprints(key: SessionKey): Map<String, String> =
        state.sessions.firstOrNull { it.sessionKey == key.asString() }?.viewedFiles ?: emptyMap()

    fun isViewed(key: SessionKey, relPath: String, fingerprint: String): Boolean =
        viewedFingerprints(key)[relPath] == fingerprint

    fun markViewed(
        key: SessionKey,
        files: Iterable<Pair<String, String>>,
        viewed: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        updateState { current ->
            val keyString = key.asString()
            val existing = current.sessions.firstOrNull { it.sessionKey == keyString }
                ?: SessionViewedState(keyString)
            val updatedFiles =
                if (viewed) existing.viewedFiles + files
                else existing.viewedFiles - files.map { it.first }.toSet()
            val updated = existing.copy(lastUpdated = nowMillis, viewedFiles = updatedFiles)
            val staleBefore = nowMillis - TimeUnit.DAYS.toMillis(STALE_DAYS)
            val others = current.sessions.filterNot { it.sessionKey == keyString }
            current.copy(
                sessions = (others + updated).filterNot {
                    it.lastUpdated < staleBefore || it.viewedFiles.isEmpty()
                }
            )
        }
    }

    companion object {
        const val STALE_DAYS = 30L
        fun getInstance(project: Project): ReviewStateService = project.service()
    }
}
