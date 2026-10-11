package de.familyplanner.core.device

import de.familyplanner.core.auth.DevicePrincipal
import de.familyplanner.core.error.AppException
import de.familyplanner.core.security.TokenHasher
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

class DeviceService(
    private val devices: DeviceRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DeviceService::class.java)

    suspend fun authenticate(token: String): DevicePrincipal? {
        val auth = devices.findAuthByTokenHash(TokenHasher.sha256Hex(token)) ?: return null
        if (auth.device.revokedAt != null) return null
        val now = clock.instant()
        val lastSeen = auth.device.lastSeenAt
        if (lastSeen == null || Duration.between(lastSeen, now) >= LAST_SEEN_UPDATE_INTERVAL) {
            devices.touch(auth.device.id, now)
        }
        return DevicePrincipal(auth.device.id, auth.member.id, auth.member.role)
    }

    suspend fun list(): List<Device> = devices.findActive()

    suspend fun revoke(id: DeviceId) {
        if (!devices.revoke(id, clock.instant())) throw AppException.NotFound("Gerät nicht gefunden")
        log.info("Device revoked: {}", id.value)
    }

    companion object {
        val LAST_SEEN_UPDATE_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
