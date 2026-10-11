package de.familyplanner.core.auth

import de.familyplanner.core.device.DeviceId
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role

data class DevicePrincipal(
    val deviceId: DeviceId,
    val memberId: MemberId,
    val role: Role,
)
