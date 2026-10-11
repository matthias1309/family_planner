package de.familyplanner.core.device

import java.time.Instant

interface DeviceRepository {
    suspend fun findAuthByTokenHash(tokenHash: String): DeviceAuth?

    suspend fun findActive(): List<Device>

    suspend fun touch(
        id: DeviceId,
        at: Instant,
    )

    /** Marks an active device as revoked; returns false if it does not exist or is already revoked. */
    suspend fun revoke(
        id: DeviceId,
        at: Instant,
    ): Boolean
}
