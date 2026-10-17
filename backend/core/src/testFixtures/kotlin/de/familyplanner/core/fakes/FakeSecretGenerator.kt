package de.familyplanner.core.fakes

import de.familyplanner.core.security.SecretGenerator

class FakeSecretGenerator(
    private var code: String = "123456",
    private val token: String = "tok",
) : SecretGenerator {
    private var tokenCount = 0

    /** Subsequent [numericCode] calls return [newCode]. */
    fun nextCode(newCode: String) {
        code = newCode
    }

    override fun numericCode(length: Int): String = code

    override fun token(): String = "$token-${tokenCount++}"
}
