package de.familyplanner.core.pairing

import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.member.MemberId
import java.time.Instant

interface PairingCodeRepository {
    /** Drops the member's previous codes (and any used/expired ones) and stores the new hash. */
    suspend fun replaceForMember(
        memberId: MemberId,
        codeHash: String,
        expiresAt: Instant,
        now: Instant,
    )

    /**
     * Atomically marks a valid, unused code as used and creates the device built by [device];
     * returns null if the code is unknown, used or expired.
     */
    suspend fun redeem(
        codeHash: String,
        now: Instant,
        device: (MemberId) -> NewDevice,
    ): RedeemedPairing?
}
