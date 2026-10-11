package de.familyplanner.core.pairing

import de.familyplanner.core.security.TokenHasher

object TokenHasherAccess {
    fun hash(value: String): String = TokenHasher.sha256Hex(value)
}
