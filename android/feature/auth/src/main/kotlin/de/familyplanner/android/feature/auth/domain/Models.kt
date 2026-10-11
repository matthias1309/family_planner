package de.familyplanner.android.feature.auth.domain

import de.familyplanner.android.core.data.session.Role
import java.time.Instant

data class Me(
    val memberId: String,
    val name: String,
    val role: Role,
    val deviceId: String,
)

data class FamilyMember(
    val id: String,
    val name: String,
    val role: Role,
)

data class FamilyDevice(
    val id: String,
    val memberId: String,
    val name: String,
    val lastSeenAt: Instant?,
)

data class IssuedPairingCode(
    val code: String,
    val expiresAt: Instant,
)

data class FamilyOverview(
    val members: List<FamilyMember>,
    val devices: List<FamilyDevice>,
)
