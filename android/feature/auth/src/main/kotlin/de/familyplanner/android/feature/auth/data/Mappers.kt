package de.familyplanner.android.feature.auth.data

import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.auth.domain.Me
import java.time.Instant
import java.time.format.DateTimeParseException
import de.familyplanner.api.model.Device as ApiDevice
import de.familyplanner.api.model.Me as ApiMe
import de.familyplanner.api.model.Member as ApiMember
import de.familyplanner.api.model.PairingCode as ApiPairingCode
import de.familyplanner.api.model.PairingResult as ApiPairingResult
import de.familyplanner.api.model.Role as ApiRole

internal fun ApiRole.toDomain(): Role =
    when (this) {
        ApiRole.PARENT -> Role.PARENT
        ApiRole.CHILD -> Role.CHILD
    }

internal fun Role.toApi(): ApiRole =
    when (this) {
        Role.PARENT -> ApiRole.PARENT
        Role.CHILD -> ApiRole.CHILD
    }

internal fun ApiPairingResult.toSession(): Session =
    Session(
        token = token,
        memberId = member.id,
        memberName = member.name,
        role = member.role.toDomain(),
        deviceId = device.id,
    )

internal fun ApiMe.toDomain(): Me = Me(member.id, member.name, member.role.toDomain(), deviceId)

internal fun ApiMember.toDomain(): FamilyMember = FamilyMember(id, name, role.toDomain())

/** `null`, wenn ein Zeitstempel nicht lesbar ist — der Aufrufer meldet dann einen Serverfehler. */
internal fun ApiDevice.toDomain(): FamilyDevice? {
    val seen = lastSeenAt?.let { parseInstant(it) ?: return null }
    return FamilyDevice(id, memberId, name, seen)
}

internal fun ApiPairingCode.toDomain(): IssuedPairingCode? = parseInstant(expiresAt)?.let { IssuedPairingCode(code, it) }

private fun parseInstant(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (
        @Suppress("SwallowedException") e: DateTimeParseException,
    ) {
        null
    }
