package de.familyplanner.core.pairing

import de.familyplanner.core.device.Device
import de.familyplanner.core.device.DeviceId
import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.DEVICE_NAME_MAX
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.security.SecretGenerator
import de.familyplanner.core.security.TokenHasher
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID

class PairingService(
    private val members: MemberRepository,
    private val codes: PairingCodeRepository,
    private val secrets: SecretGenerator,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(PairingService::class.java)

    suspend fun createCode(memberId: MemberId): IssuedPairingCode {
        members.findById(memberId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        val code = secrets.numericCode(PAIRING_CODE_LENGTH)
        val now = clock.instant()
        val expiresAt = now.plus(PAIRING_CODE_TTL)
        codes.replaceForMember(memberId, TokenHasher.sha256Hex(code), expiresAt, now)
        log.info("Pairing code issued for member {}", memberId.value)
        return IssuedPairingCode(code, expiresAt)
    }

    suspend fun pair(
        code: String,
        deviceName: String,
    ): PairingResult {
        if (!CODE_FORMAT.matches(code)) throw AppException.Validation("Der Kopplungscode besteht aus 6 Ziffern")
        val name = validatedName(deviceName, "Gerätename", DEVICE_NAME_MAX)
        val token = secrets.token()
        val now = clock.instant()
        val redeemed =
            codes.redeem(TokenHasher.sha256Hex(code), now) { memberId ->
                NewDevice(Device(DeviceId(newId()), memberId, name, now), TokenHasher.sha256Hex(token))
            }
        if (redeemed == null) {
            log.warn("Pairing failed: invalid, used or expired code")
            throw AppException.InvalidPairingCode()
        }
        log.info("Device paired: device={} member={}", redeemed.device.id.value, redeemed.member.id.value)
        return PairingResult(token, redeemed.member, redeemed.device)
    }

    companion object {
        const val PAIRING_CODE_LENGTH = 6
        val PAIRING_CODE_TTL: Duration = Duration.ofMinutes(15)
        private val CODE_FORMAT = Regex("^[0-9]{$PAIRING_CODE_LENGTH}$")
    }
}
