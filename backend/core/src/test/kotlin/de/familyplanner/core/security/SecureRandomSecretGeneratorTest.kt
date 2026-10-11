package de.familyplanner.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecureRandomSecretGeneratorTest {
    private val generator = SecureRandomSecretGenerator()

    @Test
    fun `SecureRandomSecretGenerator produces url-safe 43-char tokens`() {
        val token = generator.token()

        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(token), token)
        assertNotEquals(token, generator.token())
    }

    @Test
    fun `numeric code has requested length and only digits`() {
        repeat(50) {
            assertTrue(Regex("^[0-9]{6}$").matches(generator.numericCode(6)))
        }
    }

    @Test
    fun `sha256 hex is stable and 64 chars long`() {
        assertEquals(64, TokenHasher.sha256Hex("abc").length)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHasher.sha256Hex("abc"))
    }
}
