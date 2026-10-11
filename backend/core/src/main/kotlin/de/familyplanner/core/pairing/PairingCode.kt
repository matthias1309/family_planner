package de.familyplanner.core.pairing

import de.familyplanner.core.device.Device
import de.familyplanner.core.member.Member
import java.time.Instant

data class IssuedPairingCode(
    val code: String,
    val expiresAt: Instant,
)

data class RedeemedPairing(
    val member: Member,
    val device: Device,
)

/** Outcome of a successful setup or pairing; [token] is shown exactly once. */
data class PairingResult(
    val token: String,
    val member: Member,
    val device: Device,
)
