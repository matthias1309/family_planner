package de.familyplanner.core.security

import java.security.SecureRandom
import java.util.Base64

interface SecretGenerator {
    fun numericCode(length: Int): String

    fun token(): String
}

class SecureRandomSecretGenerator(
    private val random: SecureRandom = SecureRandom(),
) : SecretGenerator {
    override fun numericCode(length: Int): String = buildString(length) { repeat(length) { append(random.nextInt(DECIMAL_BASE)) } }

    override fun token(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val DECIMAL_BASE = 10
        const val TOKEN_BYTES = 32
    }
}
