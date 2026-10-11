package de.familyplanner.core.auth

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.Role
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import java.util.UUID

fun ApplicationCall.principalOrThrow(): DevicePrincipal = principal<DevicePrincipal>() ?: throw AppException.Unauthorized()

fun ApplicationCall.requireParent(): DevicePrincipal =
    principalOrThrow().also { if (it.role != Role.PARENT) throw AppException.Forbidden() }

/** Reads a path parameter that must be a UUID; fails with a validation error before any DB access. */
fun ApplicationCall.uuidParameter(name: String): String {
    val raw = parameters[name]
    val parsed =
        raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw AppException.Validation("Ungültige Kennung '$name'")
    return parsed.toString()
}
