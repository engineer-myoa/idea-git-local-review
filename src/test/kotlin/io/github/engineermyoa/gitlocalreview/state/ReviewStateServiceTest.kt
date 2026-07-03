package io.github.engineermyoa.gitlocalreview.state

import io.github.engineermyoa.gitlocalreview.session.DiffSpec
import io.github.engineermyoa.gitlocalreview.session.SessionKey
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewStateServiceTest {
    private val key = SessionKey("/repo", DiffSpec.BranchRange("origin/main"))
    private val otherKey = SessionKey("/repo", DiffSpec.Staged)

    @Test
    fun `mark viewed then isViewed matches only with same fingerprint`() {
        val sut = ReviewStateService()
        sut.markViewed(key, listOf("a/File.kt" to "sha1"), viewed = true)
        assertTrue(sut.isViewed(key, "a/File.kt", "sha1"))
        assertFalse(sut.isViewed(key, "a/File.kt", "sha2"))
        assertFalse(sut.isViewed(key, "b/Other.kt", "sha1"))
    }

    @Test
    fun `sessions are isolated`() {
        val sut = ReviewStateService()
        sut.markViewed(key, listOf("a/File.kt" to "sha1"), viewed = true)
        assertFalse(sut.isViewed(otherKey, "a/File.kt", "sha1"))
    }

    @Test
    fun `unmark removes entry`() {
        val sut = ReviewStateService()
        sut.markViewed(key, listOf("a/File.kt" to "sha1"), viewed = true)
        sut.markViewed(key, listOf("a/File.kt" to "sha1"), viewed = false)
        assertFalse(sut.isViewed(key, "a/File.kt", "sha1"))
        assertEquals(emptyMap<String, String>(), sut.viewedFingerprints(key))
    }

    @Test
    fun `stale sessions are pruned on update`() {
        val sut = ReviewStateService()
        val old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ReviewStateService.STALE_DAYS + 1)
        sut.markViewed(otherKey, listOf("x" to "s"), viewed = true, nowMillis = old)
        sut.markViewed(key, listOf("a/File.kt" to "sha1"), viewed = true)
        assertEquals(emptyMap<String, String>(), sut.viewedFingerprints(otherKey))
        assertTrue(sut.isViewed(key, "a/File.kt", "sha1"))
    }
}
