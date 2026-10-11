package de.familyplanner.core.pairing

import de.familyplanner.core.device.Device
import de.familyplanner.core.device.DeviceId
import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.DEVICE_NAME_MAX
import de.familyplanner.core.error.MEMBER_NAME_MAX
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.core.security.SecretGenerator
import de.familyplanner.core.security.TokenHasher
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class SetupService(
    private val configuredToken: String?,
    private val members: MemberRepository,
    private val secrets: SecretGenerator,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(SetupService::class.java)

    private fun requireValidSetupToken(setupToken: String) {
        if (configuredToken.isNullOrEmpty()) throw AppException.SetupDisabled()
        if (!TokenHasher.matches(configuredToken, setupToken)) throw AppException.Unauthorized("Setup-Token ungültig")
    }

    suspend fun setup(
        setupToken: String,
        parentName: String,
        deviceName: String,
    ): PairingResult {
        requireValidSetupToken(setupToken)
        val memberName = validatedName(parentName, "Name", MEMBER_NAME_MAX)
        val validDeviceName = validatedName(deviceName, "Gerätename", DEVICE_NAME_MAX)

        val now = clock.instant()
        val member = Member(MemberId(newId()), memberName, Role.PARENT, now)
        val token = secrets.token()
        val device = Device(DeviceId(newId()), member.id, validDeviceName, now)
        val created =
            members.createFirstParentWithDevice(member, NewDevice(device, TokenHasher.sha256Hex(token)))
                ?: throw AppException.Conflict("Einrichtung bereits erfolgt")
        log.info("Initial setup completed: member={} device={}", member.id.value, created.id.value)
        return PairingResult(token, member, created)
    }
}
