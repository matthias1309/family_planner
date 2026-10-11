package de.familyplanner.core.security

import java.security.MessageDigest

object TokenHasher {
    fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Constant-time comparison of two secrets (compares their digests). */
    fun matches(
        expected: String,
        actual: String,
    ): Boolean =
        MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(expected.toByteArray(Charsets.UTF_8)),
            MessageDigest.getInstance("SHA-256").digest(actual.toByteArray(Charsets.UTF_8)),
        )
}
