package de.familyplanner.core.web

import de.familyplanner.core.device.Device
import de.familyplanner.core.member.Member
import de.familyplanner.core.pairing.IssuedPairingCode
import de.familyplanner.core.pairing.PairingResult
import de.familyplanner.api.model.Device as ApiDevice
import de.familyplanner.api.model.Member as ApiMember
import de.familyplanner.api.model.PairingCode as ApiPairingCode
import de.familyplanner.api.model.PairingResult as ApiPairingResult
import de.familyplanner.api.model.Role as ApiRole
import de.familyplanner.core.member.Role as DomainRole

fun Member.toApi() = ApiMember(id = id.value, name = name, role = role.toApi(), createdAt = createdAt.toString())

fun Device.toApi() =
    ApiDevice(
        id = id.value,
        memberId = memberId.value,
        name = name,
        createdAt = createdAt.toString(),
        lastSeenAt = lastSeenAt?.toString(),
    )

fun PairingResult.toApi() = ApiPairingResult(token = token, member = member.toApi(), device = device.toApi())

fun IssuedPairingCode.toApi() = ApiPairingCode(code = code, expiresAt = expiresAt.toString())

fun DomainRole.toApi() = ApiRole.valueOf(name)

fun ApiRole.toDomain() = DomainRole.valueOf(name)
