package io.github.engineermyoa.gitlocalreview.git

import java.security.MessageDigest

object Fingerprints {
    const val DELETED = "deleted"

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
