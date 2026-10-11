package de.familyplanner.core.device

import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import java.time.Instant

@JvmInline
value class DeviceId(
    val value: String,
)

/** A paired device. The token (and its hash) never leaves the repository boundary except via [NewDevice]. */
data class Device(
    val id: DeviceId,
    val memberId: MemberId,
    val name: String,
    val createdAt: Instant,
    val lastSeenAt: Instant? = null,
    val revokedAt: Instant? = null,
)

data class NewDevice(
    val device: Device,
    val tokenHash: String,
)

data class DeviceAuth(
    val device: Device,
    val member: Member,
)
